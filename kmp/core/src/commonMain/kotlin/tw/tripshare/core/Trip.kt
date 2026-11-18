package tw.tripshare.core

/** A trip: the unit that members, expenses and the itinerary belong to. */
data class Trip(
    val id: String,
    val name: String,
    /** ISO 4217 code every balance is settled in. */
    val baseCurrency: String,
    /**
     * Calendar dates as "yyyy-MM-dd", free of time zones on purpose: a trip
     * to Tokyo starts on the 20th wherever the phone happens to be.
     */
    val startDate: String,
    val endDate: String,
    val ownerUid: String,
    /** Signed-in members, for the security rules. */
    val memberUids: List<String>,
    val createdAt: Long,
    /**
     * When the owner's app last opened the trip, in millis; null if it never
     * has. Read separately because it's a server timestamp, not a number.
     */
    val ownerSeenAt: Long? = null,
) {
    /** Any member may take over once the owner hasn't been seen for this long (matches firestore.rules). */
    fun ownerAbsent(now: Long): Boolean = ownerSeenAt == null || now - ownerSeenAt > OWNER_ABSENT_MILLIS

    fun toMap(): Doc = mapOf(
        "name" to name,
        "baseCurrency" to baseCurrency,
        "startDate" to startDate,
        "endDate" to endDate,
        "ownerUid" to ownerUid,
        "memberUids" to memberUids,
        "createdAt" to createdAt,
    )

    companion object {
        const val OWNER_ABSENT_MILLIS = 14L * 24 * 3600 * 1000

        fun fromMap(id: String, m: Doc) = Trip(
            id = id,
            name = m.str("name"),
            baseCurrency = m.str("baseCurrency"),
            startDate = m.str("startDate"),
            endDate = m.str("endDate"),
            ownerUid = m.str("ownerUid"),
            memberUids = m.strings("memberUids"),
            createdAt = m.long("createdAt"),
        )
    }
}

/**
 * Someone expenses can be split with. [uid] is null for a person who has
 * no phone with the app; they can claim the name later.
 */
data class Member(
    val id: String,
    val name: String,
    /** Display order, which also decides who absorbs rounding remainders. */
    val order: Long,
    val joinedAt: Long,
    val uid: String? = null,
) {
    fun toMap(): Doc = mapOf("name" to name, "uid" to uid, "order" to order, "joinedAt" to joinedAt)

    companion object {
        fun fromMap(id: String, m: Doc) = Member(
            id = id,
            name = m.str("name"),
            uid = m.strOrNull("uid"),
            order = m.long("order"),
            joinedAt = m.long("joinedAt"),
        )
    }
}
