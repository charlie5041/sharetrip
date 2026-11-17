package tw.tripshare.data

import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tw.tripshare.core.ChecklistEntry
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
        s.documents.mapNotNull { runCatching { Trip.fromMap(it.id, it.fields) }.getOrNull() }
            .sortedByDescending { it.startDate }
    }

    /** Null once the trip is gone (its owner dissolved it). */
    fun watchTrip(tripId: String): Flow<Trip?> =
        trips.document(tripId).snapshots.map { d -> if (d.exists) Trip.fromMap(d.id, d.fields) else null }

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

    // ---- The rest of a trip (screens arrive in later steps) -----------------

    fun watchItems(tripId: String): Flow<List<ItineraryItem>> =
        sub(tripId, "items").watch(ItineraryItem::fromMap).map { l -> l.sortedWith(ItineraryItem.timelineOrder) }

    fun watchFlights(tripId: String): Flow<List<Flight>> =
        sub(tripId, "flights").watch(Flight::fromMap).map { l -> l.sortedBy { it.departKey } }

    fun watchLodgings(tripId: String): Flow<List<Lodging>> =
        sub(tripId, "lodgings").watch(Lodging::fromMap).map { l -> l.sortedBy { it.checkIn } }

    fun watchChecklist(tripId: String): Flow<List<ChecklistEntry>> =
        sub(tripId, "checklist").watch(ChecklistEntry::fromMap).map { l -> l.sortedBy { it.order } }
}
