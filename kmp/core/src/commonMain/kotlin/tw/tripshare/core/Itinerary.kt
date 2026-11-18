package tw.tripshare.core

/** How you get to a stop. Shown as a coloured tag on the timeline. */
enum class Transport(val label: String) {
    none("無"), walk("步行"), train("電車"), bus("巴士"), taxi("計程車"), car("開車"), flight("飛機"), ferry("船"),
}

/** One stop on one day of the trip. */
data class ItineraryItem(
    val id: String,
    /** "yyyy-MM-dd". */
    val date: String,
    val title: String,
    /** Tie-breaker within a day, and the position of untimed stops. */
    val order: Long,
    /** "HH:mm", or null for "sometime that day". */
    val time: String? = null,
    val place: String? = null,
    val mapUrl: String? = null,
    val note: String? = null,
    /** How you get there from the previous stop. */
    val transport: Transport = Transport.none,
    /** Marked 必去 on the timeline. */
    val mustGo: Boolean = false,
) {
    fun toMap(): Doc = mapOf(
        "date" to date,
        "time" to time,
        "title" to title,
        "place" to place,
        "mapUrl" to mapUrl,
        "note" to note,
        "transport" to transport.name,
        "mustGo" to mustGo,
        "order" to order,
    )

    companion object {
        fun fromMap(id: String, m: Doc) = ItineraryItem(
            id = id,
            date = m.str("date"),
            time = m.strOrNull("time"),
            title = m.str("title"),
            place = m.strOrNull("place"),
            mapUrl = m.strOrNull("mapUrl"),
            note = m.strOrNull("note"),
            transport = Transport.entries.firstOrNull { it.name == m["transport"] } ?: Transport.none,
            mustGo = m.boolOr("mustGo", false),
            order = m.longOr("order", 0),
        )

        /** Timed stops first, by time; then untimed ones in [order]. */
        val timelineOrder: Comparator<ItineraryItem> = Comparator { a, b ->
            val at = a.time
            val bt = b.time
            when {
                at != null && bt != null -> at.compareTo(bt).takeIf { it != 0 } ?: a.order.compareTo(b.order)
                at != null -> -1
                bt != null -> 1
                else -> a.order.compareTo(b.order)
            }
        }
    }
}

/**
 * A flight leg. Times are local to each airport, as printed on the ticket,
 * so they are kept as plain text rather than converted.
 */
data class Flight(
    val id: String,
    val flightNo: String,
    val fromCode: String,
    val toCode: String,
    /** "yyyy-MM-dd" and "HH:mm", local to the departure airport. */
    val departDate: String,
    val departTime: String,
    val airline: String? = null,
    val fromName: String? = null,
    val toName: String? = null,
    /** Local to the arrival airport. */
    val arriveDate: String? = null,
    val arriveTime: String? = null,
    val note: String? = null,
) {
    val departKey: String get() = "$departDate $departTime"

    fun toMap(): Doc = mapOf(
        "flightNo" to flightNo,
        "airline" to airline,
        "fromCode" to fromCode,
        "fromName" to fromName,
        "toCode" to toCode,
        "toName" to toName,
        "departDate" to departDate,
        "departTime" to departTime,
        "arriveDate" to arriveDate,
        "arriveTime" to arriveTime,
        "note" to note,
    )

    companion object {
        fun fromMap(id: String, m: Doc) = Flight(
            id = id,
            flightNo = m.str("flightNo"),
            airline = m.strOrNull("airline"),
            fromCode = m.str("fromCode"),
            fromName = m.strOrNull("fromName"),
            toCode = m.str("toCode"),
            toName = m.strOrNull("toName"),
            departDate = m.str("departDate"),
            departTime = m.str("departTime"),
            arriveDate = m.strOrNull("arriveDate"),
            arriveTime = m.strOrNull("arriveTime"),
            note = m.strOrNull("note"),
        )
    }
}

/** Somewhere the group sleeps. */
data class Lodging(
    val id: String,
    val name: String,
    /** "yyyy-MM-dd". */
    val checkIn: String,
    val checkOut: String,
    val address: String? = null,
    val mapUrl: String? = null,
    /** Booking number, shown so anyone can check in. */
    val confirmation: String? = null,
    val note: String? = null,
) {
    fun toMap(): Doc = mapOf(
        "name" to name,
        "checkIn" to checkIn,
        "checkOut" to checkOut,
        "address" to address,
        "mapUrl" to mapUrl,
        "confirmation" to confirmation,
        "note" to note,
    )

    companion object {
        fun fromMap(id: String, m: Doc) = Lodging(
            id = id,
            name = m.str("name"),
            checkIn = m.str("checkIn"),
            checkOut = m.str("checkOut"),
            address = m.strOrNull("address"),
            mapUrl = m.strOrNull("mapUrl"),
            confirmation = m.strOrNull("confirmation"),
            note = m.strOrNull("note"),
        )
    }
}

/** Which shared list an entry belongs to. */
enum class ChecklistKind(val label: String, val suggestions: List<String>) {
    todo("待辦", emptyList()),
    predeparture(
        "出發前",
        listOf("確認護照效期（6 個月以上）", "換外幣 / 開通海外刷卡", "買網卡或開漫遊", "線上報到、選位", "買旅遊平安險", "確認住宿訂房"),
    ),
    packing("行李", listOf("護照", "手機充電器", "行動電源", "轉接頭", "換洗衣物", "盥洗用品", "常備藥", "雨傘")),
}

/** One line on a shared checklist; ticking it shows for everyone. */
data class ChecklistEntry(
    val id: String,
    val kind: ChecklistKind,
    val text: String,
    val done: Boolean,
    val order: Long,
    /** Name of whoever ticked it. */
    val doneBy: String? = null,
) {
    fun toMap(): Doc = mapOf("kind" to kind.name, "text" to text, "done" to done, "doneBy" to doneBy, "order" to order)

    companion object {
        fun fromMap(id: String, m: Doc) = ChecklistEntry(
            id = id,
            kind = ChecklistKind.entries.firstOrNull { it.name == m["kind"] } ?: ChecklistKind.todo,
            text = m.str("text"),
            done = m.boolOr("done", false),
            doneBy = m.strOrNull("doneBy"),
            order = m.longOr("order", 0),
        )
    }
}
