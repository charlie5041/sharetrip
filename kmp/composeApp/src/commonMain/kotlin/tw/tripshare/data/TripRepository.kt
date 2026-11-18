package tw.tripshare.data

import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tw.tripshare.core.ChecklistEntry
import tw.tripshare.core.ChecklistKind
import tw.tripshare.core.Doc
import tw.tripshare.core.Expense
import tw.tripshare.core.Flight
import tw.tripshare.core.InviteCodes
import tw.tripshare.core.InviteError
import tw.tripshare.core.InviteException
import tw.tripshare.core.ItineraryItem
import tw.tripshare.core.Lodging
import tw.tripshare.core.Member
import tw.tripshare.core.Settlement
import tw.tripshare.core.Trip
import tw.tripshare.nowMillis
import tw.tripshare.secureRandomBytes

/** An invite that checked out, before this device actually joins. */
data class PendingJoin(val code: String, val tripId: String)

/** A step refused before it reached the server, worded for the person. */
class RefusedException(message: String) : Exception(message)

/**
 * Every Firestore read and write the app makes, for one signed-in device.
 * Same collections and fields as the Flutter app's trip_repository.dart, so
 * both apps work on the same trips.
 */
class TripRepository(private val db: FirebaseFirestore, val uid: String) {

    private val trips get() = db.collection("trips")
    private val userDoc get() = db.collection("users").document(uid)
    private fun sub(tripId: String, name: String) = trips.document(tripId).collection(name)
    private fun members(tripId: String) = sub(tripId, "members")

    /** The raw fields; numbers come back as Long or Double depending on platform. */
    @Suppress("UNCHECKED_CAST")
    private val DocumentSnapshot.fields: Doc get() = (compat.data ?: emptyMap<String, Any?>()) as Doc

    private fun <T> CollectionReference.watch(read: (String, Doc) -> T): Flow<List<T>> =
        snapshots.map { s -> s.documents.mapNotNull { d -> runCatching { read(d.id, d.fields) }.getOrNull() } }

    // ---- Profile -----------------------------------------------------------

    suspend fun loadNickname(): String? = userDoc.get().takeIf { it.exists }?.fields?.get("nickname") as? String

    suspend fun setNickname(nickname: String) = userDoc.set(mapOf("nickname" to nickname.trim()), merge = true)

    // ---- Trips -------------------------------------------------------------

    fun watchMyTrips(): Flow<List<Trip>> = trips.where { "memberUids" contains uid }.snapshots.map { s ->
        s.documents.mapNotNull { runCatching { it.trip() }.getOrNull() }
            .sortedByDescending { it.startDate }
    }

    /** Null once the trip is gone (its owner dissolved it). */
    fun watchTrip(tripId: String): Flow<Trip?> =
        trips.document(tripId).snapshots.map { d -> if (d.exists) d.trip() else null }

    private fun DocumentSnapshot.trip(): Trip {
        val seen = runCatching { get<Timestamp?>("ownerSeenAt") }.getOrNull()
        return Trip.fromMap(id, fields).copy(ownerSeenAt = seen?.let { it.seconds * 1000 + it.nanoseconds / 1_000_000 })
    }

    /** A fresh trip id, so a screen can open the trip before the write reaches the server. */
    fun newTripId(): String = trips.document.id

    /**
     * Creates a trip with this device as its owner and first member, plus a
     * place for each of [others]; friends take those places when they join.
     */
    suspend fun createTrip(
        id: String,
        name: String,
        baseCurrency: String,
        startDate: String,
        endDate: String,
        nickname: String,
        others: List<String>,
    ) {
        val now = nowMillis()
        val ref = trips.document(id)
        val batch = db.batch()
        batch.set(
            ref,
            mapOf(
                "name" to name.trim(), "baseCurrency" to baseCurrency, "startDate" to startDate,
                "endDate" to endDate, "ownerUid" to uid, "memberUids" to listOf(uid), "createdAt" to now,
                "ownerSeenAt" to FieldValue.serverTimestamp,
            ),
        )
        batch.set(members(id).document(uid), mapOf("name" to nickname, "uid" to uid, "order" to 0L, "joinedAt" to now))
        others.map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, n ->
            batch.set(members(id).document, mapOf("name" to n, "uid" to null, "order" to (i + 1).toLong(), "joinedAt" to now))
        }
        batch.commit()
    }

    // ---- Members -----------------------------------------------------------

    fun watchMembers(tripId: String): Flow<List<Member>> =
        members(tripId).watch(Member::fromMap).map { l -> l.sortedBy { it.order } }

    /** Adds a place for someone, by name. Owner only. */
    suspend fun addMember(tripId: String, name: String) {
        val next = (members(tripId).get().documents.maxOfOrNull { (it.fields["order"] as? Number)?.toLong() ?: 0 } ?: -1) + 1
        members(tripId).add(mapOf("name" to name.trim(), "uid" to null, "order" to next, "joinedAt" to nowMillis()))
    }

    suspend fun renameMember(tripId: String, memberId: String, name: String) =
        members(tripId).document(memberId).update("name" to name.trim())

    /**
     * Removes [m] from the trip. Owner only. Refused while an expense or a
     * settlement still points at them, so no balance changes behind anyone's back.
     */
    suspend fun removeMember(tripId: String, m: Member) {
        if (isReferenced(tripId, m.id)) throw RefusedException("「${m.name}」已經有記帳紀錄，要先刪掉或改掉相關的帳才能移除")
        val batch = db.batch().delete(members(tripId).document(m.id))
        m.uid?.let { batch.update(trips.document(tripId), "memberUids" to FieldValue.arrayRemove(it)) }
        batch.commit()
    }

    /** Frees [m]'s place, keeping their name and expenses, so they can take it again from a new phone. */
    suspend fun unbindMember(tripId: String, m: Member) {
        val u = m.uid ?: return
        db.batch()
            .update(members(tripId).document(m.id), "uid" to null)
            .update(trips.document(tripId), "memberUids" to FieldValue.arrayRemove(u))
            .commit()
    }

    /**
     * Leaves the trip. Not for the owner, who hands the trip over first.
     * The name stays on the list as an open place, with its expenses, so
     * balances don't change and the person can take it back with an invite.
     */
    suspend fun leaveTrip(tripId: String, me: Member) {
        db.batch()
            .update(members(tripId).document(me.id), "uid" to null)
            .update(trips.document(tripId), "memberUids" to FieldValue.arrayRemove(uid))
            .commit()
    }

    /** Moves this device from its place [from] to the open place [to], e.g. back to the name it created the trip as. */
    suspend fun moveToPlace(tripId: String, from: Member?, to: Member) {
        val batch = db.batch()
        from?.let { batch.update(members(tripId).document(it.id), "uid" to null) }
        batch.update(members(tripId).document(to.id), "uid" to uid, "joinedAt" to nowMillis())
        batch.commit()
    }

    /** Hands the trip to another joined member. Owner only. */
    suspend fun transferOwner(tripId: String, toUid: String) =
        trips.document(tripId).update("ownerUid" to toUid, "ownerSeenAt" to FieldValue.serverTimestamp)

    /** Takes over a trip whose owner hasn't opened it for two weeks (or lost their account). */
    suspend fun claimOwner(tripId: String) =
        trips.document(tripId).update("ownerUid" to uid, "ownerSeenAt" to FieldValue.serverTimestamp)

    /** The owner's app marks that the owner is still around, so nobody takes the trip over. */
    suspend fun stampOwnerSeen(tripId: String) =
        trips.document(tripId).update("ownerSeenAt" to FieldValue.serverTimestamp)

    /** Deletes the trip and everything in it. Owner only. */
    suspend fun dissolveTrip(tripId: String) {
        for (name in listOf("expenses", "settlements", "items", "flights", "lodgings", "checklist", "members")) {
            sub(tripId, name).get().documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit()
            }
        }
        trips.document(tripId).delete()
    }

    private suspend fun isReferenced(tripId: String, memberId: String): Boolean {
        val referencedByExpense = sub(tripId, "expenses").get().documents.any { d ->
            val f = d.fields
            f["payerId"] == memberId || (f["splits"] as? Map<*, *>)?.containsKey(memberId) == true
        }
        if (referencedByExpense) return true
        return sub(tripId, "settlements").get().documents.any { d ->
            d.fields["fromId"] == memberId || d.fields["toId"] == memberId
        }
    }

    // ---- Invites -----------------------------------------------------------

    suspend fun createInvite(tripId: String): String {
        val code = InviteCodes.fromBytes(secureRandomBytes(InviteCodes.LENGTH))
        val expires = nowMillis() + InviteCodes.TTL_MILLIS
        db.collection("invites").document(code).set(
            mapOf(
                "tripId" to tripId,
                "expiresAt" to Timestamp(expires / 1000, ((expires % 1000) * 1_000_000).toInt()),
                "createdBy" to uid,
            ),
        )
        return code
    }

    /** Checks [rawCode] and records it on this device's user document, which the security rules read to allow the join. */
    suspend fun checkInvite(rawCode: String): PendingJoin {
        val code = InviteCodes.normalize(rawCode)
        if (code.length != InviteCodes.LENGTH) throw InviteException(InviteError.notFound)
        val snap = db.collection("invites").document(code).get()
        if (!snap.exists) throw InviteException(InviteError.notFound)
        val expires = snap.get<Timestamp>("expiresAt")
        if (expires.seconds * 1000 < nowMillis()) throw InviteException(InviteError.expired)
        val tripId = snap.fields["tripId"] as String
        val mine = trips.where { "memberUids" contains uid }.get().documents
        if (mine.any { it.id == tripId }) throw InviteException(InviteError.alreadyMember)
        userDoc.set(mapOf("joinCode" to code), merge = true)
        return PendingJoin(code, tripId)
    }

    /** The places nobody has taken yet, for someone holding a checked invite. */
    suspend fun openPlaces(pending: PendingJoin): List<Member> =
        members(pending.tripId).where { "uid".isNull }.get().documents
            .map { Member.fromMap(it.id, it.fields) }.sortedBy { it.order }

    /** Joins the trip by taking the open place [memberId]. */
    suspend fun join(pending: PendingJoin, memberId: String) {
        db.batch()
            .update(trips.document(pending.tripId), "memberUids" to FieldValue.arrayUnion(uid))
            .update(members(pending.tripId).document(memberId), "uid" to uid, "joinedAt" to nowMillis())
            .commit()
    }

    // ---- Expenses and settlements ------------------------------------------

    fun watchExpenses(tripId: String): Flow<List<Expense>> =
        sub(tripId, "expenses").watch(Expense::fromMap).map { l -> l.sortedByDescending { it.occurredAt } }

    /** Creates [e] when its id is empty, otherwise replaces it. */
    suspend fun saveExpense(tripId: String, e: Expense) {
        val data = e.toMap() + mapOf("updatedAt" to nowMillis(), "createdBy" to e.createdBy.ifEmpty { uid })
        if (e.id.isEmpty()) sub(tripId, "expenses").add(data) else sub(tripId, "expenses").document(e.id).set(data)
    }

    suspend fun deleteExpense(tripId: String, expenseId: String) = sub(tripId, "expenses").document(expenseId).delete()

    fun watchSettlements(tripId: String): Flow<List<Settlement>> =
        sub(tripId, "settlements").watch(Settlement::fromMap).map { l -> l.sortedByDescending { it.at } }

    suspend fun addSettlement(tripId: String, fromId: String, toId: String, amountMinor: Long) {
        sub(tripId, "settlements").add(
            mapOf("fromId" to fromId, "toId" to toId, "amountMinor" to amountMinor, "at" to nowMillis(), "createdBy" to uid),
        )
    }

    suspend fun deleteSettlement(tripId: String, id: String) = sub(tripId, "settlements").document(id).delete()

    // ---- Itinerary, flights, lodging, checklists ---------------------------

    fun watchItems(tripId: String): Flow<List<ItineraryItem>> =
        sub(tripId, "items").watch(ItineraryItem::fromMap).map { l -> l.sortedWith(ItineraryItem.timelineOrder) }

    /** New stops go last among the untimed ones of their day. */
    suspend fun saveItem(tripId: String, item: ItineraryItem) {
        if (item.id.isEmpty()) sub(tripId, "items").add(item.toMap() + ("order" to nowMillis()))
        else sub(tripId, "items").document(item.id).set(item.toMap())
    }

    suspend fun deleteItem(tripId: String, id: String) = sub(tripId, "items").document(id).delete()

    fun watchFlights(tripId: String): Flow<List<Flight>> =
        sub(tripId, "flights").watch(Flight::fromMap).map { l -> l.sortedBy { it.departKey } }

    suspend fun saveFlight(tripId: String, f: Flight) {
        if (f.id.isEmpty()) sub(tripId, "flights").add(f.toMap()) else sub(tripId, "flights").document(f.id).set(f.toMap())
    }

    suspend fun deleteFlight(tripId: String, id: String) = sub(tripId, "flights").document(id).delete()

    fun watchLodgings(tripId: String): Flow<List<Lodging>> =
        sub(tripId, "lodgings").watch(Lodging::fromMap).map { l -> l.sortedBy { it.checkIn } }

    suspend fun saveLodging(tripId: String, l: Lodging) {
        if (l.id.isEmpty()) sub(tripId, "lodgings").add(l.toMap()) else sub(tripId, "lodgings").document(l.id).set(l.toMap())
    }

    suspend fun deleteLodging(tripId: String, id: String) = sub(tripId, "lodgings").document(id).delete()

    fun watchChecklist(tripId: String): Flow<List<ChecklistEntry>> =
        sub(tripId, "checklist").watch(ChecklistEntry::fromMap).map { l -> l.sortedBy { it.order } }

    suspend fun addChecklistEntries(tripId: String, kind: ChecklistKind, texts: List<String>) {
        val batch = db.batch()
        var order = nowMillis()
        texts.map { it.trim() }.filter { it.isNotEmpty() }.forEach { t ->
            batch.set(
                sub(tripId, "checklist").document,
                mapOf("kind" to kind.name, "text" to t, "done" to false, "doneBy" to null, "order" to order++),
            )
        }
        batch.commit()
    }

    suspend fun setChecklistDone(tripId: String, id: String, done: Boolean, byName: String) =
        sub(tripId, "checklist").document(id).update("done" to done, "doneBy" to if (done) byName else null)

    suspend fun deleteChecklistEntry(tripId: String, id: String) = sub(tripId, "checklist").document(id).delete()

    // ---- Import from a spreadsheet -----------------------------------------

    /** An id for a member the import will create, so expenses can point at them before they exist. */
    fun newMemberId(tripId: String): String = members(tripId).document.id

    /**
     * Writes one import: new members first, then everything else, each
     * document tagged with [importId] so [undoImport] can take it all back.
     * Firestore takes at most 500 writes per batch, so larger imports go in
     * several; members are always in the first.
     */
    suspend fun importAll(
        tripId: String,
        importId: String,
        newMembers: List<Pair<String, String>>,
        firstOrder: Long,
        expenses: List<Expense>,
        items: List<ItineraryItem>,
        lodgings: List<Lodging>,
        flights: List<Flight>,
        checklist: List<Pair<ChecklistKind, String>>,
    ) {
        val now = nowMillis()
        val tag = mapOf("importId" to importId)
        val writes = mutableListOf<Pair<dev.gitlive.firebase.firestore.DocumentReference, Doc>>()
        newMembers.forEachIndexed { i, (id, name) ->
            writes += members(tripId).document(id) to
                mapOf("name" to name.trim(), "uid" to null, "order" to firstOrder + i, "joinedAt" to now) + tag
        }
        expenses.forEach { e ->
            writes += sub(tripId, "expenses").document to e.toMap() + mapOf("updatedAt" to now, "createdBy" to uid) + tag
        }
        items.forEachIndexed { i, it -> writes += sub(tripId, "items").document to it.toMap() + ("order" to now + i) + tag }
        lodgings.forEach { writes += sub(tripId, "lodgings").document to it.toMap() + tag }
        flights.forEach { writes += sub(tripId, "flights").document to it.toMap() + tag }
        checklist.forEachIndexed { i, (kind, text) ->
            writes += sub(tripId, "checklist").document to
                mapOf("kind" to kind.name, "text" to text, "done" to false, "doneBy" to null, "order" to now + i) + tag
        }
        writes.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit()
        }
    }

    /** Deletes what [importId] wrote and nobody has edited since (an edit drops the tag). */
    suspend fun undoImport(tripId: String, importId: String) {
        for (name in listOf("expenses", "items", "lodgings", "flights", "checklist", "members")) {
            sub(tripId, name).where { "importId" equalTo importId }.get().documents
                .filter { name != "members" || it.fields["uid"] == null }
                .chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit()
                }
        }
    }

    /**
     * Earlier imports whose documents are still tagged, with how many of each
     * kind are left, so one can be taken back after leaving the import screen.
     */
    suspend fun pastImports(tripId: String): List<PastImport> {
        val counts = mutableMapOf<String, MutableMap<String, Int>>()
        for (name in listOf("expenses", "items", "lodgings", "flights", "checklist")) {
            sub(tripId, name).get().documents.forEach { d ->
                val id = d.fields["importId"] as? String ?: return@forEach
                counts.getOrPut(id) { mutableMapOf() }.let { it[name] = (it[name] ?: 0) + 1 }
            }
        }
        return counts.map { (id, c) -> PastImport(id, c) }
    }

    /** Moves the trip's dates, e.g. so imported days fall inside it. */
    suspend fun updateTripDates(tripId: String, start: String, end: String) =
        trips.document(tripId).update("startDate" to start, "endDate" to end)
}

/** One earlier import: documents left per collection name. */
data class PastImport(val id: String, val counts: Map<String, Int>)
