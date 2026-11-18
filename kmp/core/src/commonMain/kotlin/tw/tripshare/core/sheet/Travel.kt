package tw.tripshare.core.sheet

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import tw.tripshare.core.ChecklistKind
import tw.tripshare.core.Transport
import tw.tripshare.core.parseYmd
import tw.tripshare.core.ymd

/** One stop read from the sheet. */
data class ItemRow(
    val date: String,
    val time: String?,
    val title: String,
    val place: String? = null,
    val note: String? = null,
    val transport: Transport = Transport.none,
)

data class LodgingRow(
    val name: String,
    val checkIn: String,
    val checkOut: String,
    val address: String? = null,
    val confirmation: String? = null,
    val note: String? = null,
)

data class FlightRow(
    val flightNo: String,
    val fromCode: String,
    val toCode: String,
    val departDate: String,
    val departTime: String,
    val airline: String? = null,
    val arriveDate: String? = null,
    val arriveTime: String? = null,
    val note: String? = null,
)

data class ChecklistRow(val kind: ChecklistKind, val text: String)

/** A row that looked like data but couldn't be read, shown so nothing vanishes silently. */
data class Skipped(val what: String, val text: String, val reason: String)

internal class TravelOut {
    val items = mutableListOf<ItemRow>()
    val lodgings = mutableListOf<LodgingRow>()
    val flights = mutableListOf<FlightRow>()
    val checklist = mutableListOf<ChecklistRow>()
    val skipped = mutableListOf<Skipped>()
}

private val LODGING_LABELS = listOf("住宿", "飯店", "酒店", "旅館", "hotel", "🛌")
private val ROW_LABELS = listOf("行程", "美食", "採買", "購物", "餐廳", "景點", "交通", "備註")
private fun isLabelWord(s: String) = s.length in 1..6 && s.none { it.isDigit() } &&
    (isLodgingLabel(s) || ROW_LABELS.any { s.contains(it) })
private fun isLodgingLabel(s: String) = s.isNotEmpty() && LODGING_LABELS.any { s.lowercase().contains(it) }

private const val MAX_TITLE = 60

/** A cell's first line as the title, the rest (and any overflow) as the note. */
private fun titleAndNote(text: String): Pair<String, String?> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return "" to null
    val first = lines.first()
    val title = if (first.length > MAX_TITLE) first.take(MAX_TITLE) + "…" else first
    val rest = (if (first.length > MAX_TITLE) listOf(first) else emptyList()) + lines.drop(1)
    return title to rest.joinToString("\n").ifEmpty { null }
}

private fun joinNotes(vararg parts: String?): String? = parts.filterNotNull().filter { it.isNotBlank() }.joinToString("\n").ifEmpty { null }

/**
 * Days laid out across the sheet: one header row of dates, each day owning
 * the columns up to the next date (one for "模板", two for time + place),
 * with an optional label column on the left ("住宿 🛌", "行程 📋").
 */
internal fun findDayColumns(s: Section, ctx: ImportContext, out: TravelOut): Boolean {
    for (r in s.rows.indices) {
        val dateCols = s.rows[r].withIndex()
            .mapNotNull { (c, v) -> parseSheetDate(v, ctx.tripStart)?.let { c to it } }
        if (dateCols.size < 2) continue
        val dates = dateCols.map { it.second }
        if (dates.zipWithNext().any { (a, b) -> b <= a }) continue // Not a run of days.

        val widths = dateCols.zipWithNext { a, b -> b.first - a.first }
        val lastWidth = widths.lastOrNull() ?: 1
        val groups = dateCols.mapIndexed { i, (c, d) ->
            var w = widths.getOrElse(i) { lastWidth }.coerceAtMost(3)
            // The last day stops at an empty header column when another table follows.
            if (i == dateCols.lastIndex) {
                var k = 1
                while (k < w && s.rows.drop(r + 1).take(40).any { it.getOrNull(c + k).orEmpty().isNotEmpty() } &&
                    s.cell(r, c + k).isEmpty()) k++
                w = k
            }
            Triple(c, w, d)
        }
        val labelCol = dateCols.first().first - 1
        var label = ""
        val lodgingLines = groups.associate { it.third to mutableListOf<String>() }
        val gridCols = (maxOf(labelCol, 0)..groups.last().let { it.first + it.second - 1 })
        var blankRun = 0
        var afterLodging = false
        for (row in r + 1 until s.rows.size) {
            if (gridCols.all { s.cell(row, it).isEmpty() }) {
                blankRun++
                if (isLodgingLabel(label)) { label = ""; afterLodging = true }
                continue
            }
            // A row label sits left of the days, or in the first day's time column.
            val g0 = groups.first()
            val inTimeCol = g0.second >= 2 && isLabelWord(s.cell(row, g0.first))
            val rowLabel = when {
                labelCol >= 0 -> s.cell(row, labelCol)
                inTimeCol -> s.cell(row, g0.first)
                else -> ""
            }
            if (rowLabel.isNotEmpty()) {
                label = rowLabel
                afterLodging = false
            } else if (afterLodging) {
                break // Stays come last; what follows is another table.
            } else if (blankRun >= 3 && groups.none { (c, w, _) -> (c until c + w).any { splitTime(s.cell(row, it)).time != null } }) {
                break // A long gap, then untimed rows: another table.
            }
            blankRun = 0
            for ((c, w, date) in groups) {
                val cells = (c until c + w).map { if (inTimeCol && it == g0.first) "" else s.cell(row, it) }
                if (isLodgingLabel(label)) {
                    cells.filter { it.isNotEmpty() }.forEach { lodgingLines.getValue(date) += it }
                    continue
                }
                val item = dayCell(date, if (w >= 2) cells[0] else "", if (w >= 2) cells.drop(1).filter { it.isNotEmpty() }.joinToString("\n") else cells[0])
                    ?: continue
                dayFlight(item)?.let { out.flights += it; continue }
                val extra = label.takeIf { it.isNotEmpty() && !it.contains("行程") }
                out.items += item.copy(note = joinNotes(item.note, extra?.let { "（$it）" }))
            }
        }
        out.lodgings += mergeNights(dates, lodgingLines)
        return true
    }
    return false
}

/** One cell of a day column → a stop. [timeCell] is the separate time column, if any. */
private fun dayCell(date: String, timeCell: String, text: String): ItemRow? {
    if (text.isBlank()) return null
    val timed = splitTime(text)
    val sideTime = splitTime(timeCell)
    val time = timed.time ?: sideTime.time
    val (title, note) = titleAndNote(timed.rest.ifEmpty { text })
    if (title.isEmpty()) return null
    val until = timed.until ?: sideTime.until
    val timeNote = when {
        timed.time != null && until != null -> "${timed.time}–$until"
        sideTime.time != null && until != null -> "${sideTime.time}–$until"
        sideTime.time == null && timeCell.isNotBlank() -> timeCell // "20min", "option 1"
        sideTime.time != null && sideTime.rest.isNotEmpty() -> sideTime.rest
        else -> null
    }
    return ItemRow(date, time, title, note = joinNotes(timeNote, note), transport = guessTransport(text))
}

/**
 * Lodging cells per night → stays, merging nights at the same place. With
 * several cells a night (模板: city, hotel, room), the second is the hotel;
 * a single cell starts with the hotel's name.
 */
private fun mergeNights(dates: List<String>, cells: Map<String, List<String>>): List<LodgingRow> {
    data class Night(val date: String, val name: String, val note: String?)
    val nights = dates.mapNotNull { d ->
        val c = cells[d].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        if (c.isEmpty()) return@mapNotNull null
        val nameCell = if (c.size >= 2) 1 else 0
        val name = c[nameCell].lines().first().trim()
        val rest = c.flatMapIndexed { i, cell -> if (i == nameCell) cell.lines().drop(1) else cell.lines() }
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (c.size == 1 && NOT_A_STAY.any { name.contains(it) }) return@mapNotNull null
        Night(d, name, rest.joinToString(" · ").ifEmpty { null })
    }
    val out = mutableListOf<LodgingRow>()
    for (n in nights) {
        val last = out.lastOrNull()
        if (last != null && nameKey(last.name) == nameKey(n.name) && last.checkOut == n.date) {
            out[out.lastIndex] = last.copy(checkOut = nextDay(n.date))
        } else {
            out += LodgingRow(n.name.take(80), n.date, nextDay(n.date), note = n.note)
        }
    }
    return out
}

/** "溫暖的家" (home) or a night on the plane: not a stay to book. */
private val NOT_A_STAY = listOf("家", "飛機", "機上", "夜車")

private fun nextDay(d: String) = ymd(parseYmd(d).plus(DatePeriod(days = 1)))

// ---- Row tables (one stop per row) -----------------------------------------

private object T {
    val date = listOf("日期", "date", "day")
    val time = listOf("時間", "time")
    val title = listOf("行程", "活動", "內容", "景點", "安排", "項目", "備註")
    val place = listOf("地點", "地址", "place", "城市")
    val transport = listOf("交通", "交通方式", "transport")
    val note = listOf("備註", "說明", "note")
    val lodging = listOf("住宿", "飯店", "hotel")
    val checkIn = listOf("入住", "入住日", "入住日期", "checkin")
    val checkOut = listOf("退房", "退房日", "退房日期", "checkout")
    val lodgingName = listOf("住宿", "名稱", "飯店", "旅館", "住宿名稱", "hotel")
    val address = listOf("地址", "address")
    val confirmation = listOf("訂房代碼", "訂房編號", "確認碼", "訂單編號", "confirmation")
    val flightNo = listOf("航班", "班機", "航班號碼", "航班編號", "flight")
    val airline = listOf("航空公司", "航空", "airline")
    val from = listOf("出發機場", "起飛機場", "出發地", "from")
    val to = listOf("抵達機場", "降落機場", "目的地", "to")
    val departDate = listOf("出發日期", "起飛日期", "日期")
    val departTime = listOf("出發時間", "起飛時間", "時間")
    val arriveDate = listOf("抵達日期", "降落日期")
    val arriveTime = listOf("抵達時間", "降落時間")
    val list = listOf("清單", "list")
    val entry = listOf("項目", "內容", "item")
}

private fun List<String>.find(names: List<String>, except: Set<Int> = emptySet()): Int? =
    names.firstNotNullOfOrNull { n -> indices.firstOrNull { it !in except && this[it] == n } }

/** Reads the row tables in [s]: stops, stays, flights and checklist entries. */
internal fun findRowTables(s: Section, ctx: ImportContext, out: TravelOut) {
    val sectionKind = s.title?.let(::checklistKindOf)
    var r = 0
    while (r < s.rows.size) {
        val keys = s.rows[r].map(::headerKey)
        val end = when {
            keys.find(T.checkIn) != null && keys.find(T.checkOut) != null -> lodgingTable(s, r, keys, ctx, out)
            keys.find(T.flightNo) != null && keys.find(T.from) != null -> flightTable(s, r, keys, ctx, out)
            keys.find(T.list) != null && keys.find(T.entry) != null -> checklistTable(s, r, keys, null, out)
            sectionKind != null && keys.find(T.entry) != null && !isExpenseHeader(keys) && keys.find(T.date) == null ->
                checklistTable(s, r, keys, sectionKind, out)
            keys.find(T.date) != null && keys.find(T.title) != null && !isExpenseHeader(keys) -> stopTable(s, r, keys, ctx, out)
            else -> null
        }
        r = (end ?: r) + 1
    }
}

private fun checklistKindOf(text: String): ChecklistKind? = when {
    text.contains("行李") || text.contains("打包") || text.contains("攜帶") -> ChecklistKind.packing
    text.contains("出發前") -> ChecklistKind.predeparture
    text.contains("待辦") || text.contains("清單") -> ChecklistKind.todo
    else -> null
}

private fun rowsAfter(s: Section, r: Int) = (r + 1 until s.rows.size).takeWhile { row ->
    s.rows[row].map(::headerKey).let { k -> !(k.find(T.date) != null && k.find(T.title) != null) && k.find(T.checkIn) == null }
}

private fun stopTable(s: Section, r: Int, keys: List<String>, ctx: ImportContext, out: TravelOut): Int {
    val dateCol = keys.find(T.date)!!
    val titleCol = keys.find(T.title)!!
    val timeCol = keys.find(T.time)
    val placeCol = keys.find(T.place, setOf(titleCol))
    val transportCol = keys.find(T.transport)
    val noteCol = keys.find(T.note, setOf(titleCol))
    val lodgingCol = keys.find(T.lodging, setOf(titleCol))
    var date: String? = null
    val nights = linkedMapOf<String, MutableList<String>>()
    val rows = rowsAfter(s, r)
    for (row in rows) {
        s.cell(row, dateCol).takeIf { it.isNotEmpty() }?.let { date = parseSheetDate(it, ctx.tripStart) ?: date }
        val text = s.cell(row, titleCol)
        val d = date
        lodgingCol?.let { c -> s.cell(row, c).takeIf { it.isNotEmpty() && d != null }?.let { nights.getOrPut(d!!) { mutableListOf() } += it } }
        if (text.isEmpty()) continue
        if (d == null) { out.skipped += Skipped("行程", text, "找不到日期"); continue }
        val timeText = timeCol?.let { s.cell(row, it) }.orEmpty()
        val item = dayCell(d, timeText, text) ?: continue
        dayFlight(item)?.let { out.flights += it; continue }
        val transportText = transportCol?.let { s.cell(row, it) }.orEmpty()
        out.items += item.copy(
            place = placeCol?.let { s.cell(row, it) }?.ifEmpty { null },
            note = joinNotes(item.note, noteCol?.let { s.cell(row, it) }),
            transport = if (transportText.isNotEmpty()) transportOf(transportText) else item.transport,
        )
    }
    // A stay written on its first night only still covers the nights until the next one.
    val dates = nights.keys.toList()
    if (dates.isNotEmpty()) {
        val all = (tw.tripshare.core.datesBetween(dates.first(), dates.last()))
        out.lodgings += mergeNights(all, all.associateWith { d -> nights[d] ?: nights.entries.lastOrNull { it.key < d }?.value.orEmpty() })
    }
    return rows.lastOrNull() ?: r
}

private fun lodgingTable(s: Section, r: Int, keys: List<String>, ctx: ImportContext, out: TravelOut): Int {
    val inCol = keys.find(T.checkIn)!!
    val outCol = keys.find(T.checkOut)!!
    val nameCol = keys.find(T.lodgingName, setOf(inCol, outCol))
    val addressCol = keys.find(T.address)
    val confCol = keys.find(T.confirmation)
    val noteCol = keys.find(T.note)
    var last = r
    for (row in r + 1 until s.rows.size) {
        if (s.rows[row].all { it.isEmpty() }) continue
        last = row
        val name = nameCol?.let { s.cell(row, it) }.orEmpty()
        val ci = parseSheetDate(s.cell(row, inCol), ctx.tripStart)
        val co = parseSheetDate(s.cell(row, outCol), ctx.tripStart)
        when {
            name.isEmpty() -> out.skipped += Skipped("住宿", s.rows[row].joinToString(" "), "沒有住宿名稱")
            ci == null || co == null -> out.skipped += Skipped("住宿", name, "入住或退房日期看不懂")
            co <= ci -> out.skipped += Skipped("住宿", name, "退房要晚於入住")
            else -> out.lodgings += LodgingRow(
                name.take(80), ci, co,
                address = addressCol?.let { s.cell(row, it) }?.ifEmpty { null },
                confirmation = confCol?.let { s.cell(row, it) }?.ifEmpty { null },
                note = noteCol?.let { s.cell(row, it) }?.ifEmpty { null },
            )
        }
    }
    return last
}

private val AIRPORT = Regex("""\b([A-Z]{3})\b""")
private val FLIGHT_NO = Regex("""\b([A-Z0-9]{2}\s?\d{1,4}[A-Z]?)\b""")

private fun flightTable(s: Section, r: Int, keys: List<String>, ctx: ImportContext, out: TravelOut): Int {
    val noCol = keys.find(T.flightNo)!!
    val fromCol = keys.find(T.from)!!
    val toCol = keys.find(T.to)
    val airlineCol = keys.find(T.airline)
    val dDateCol = keys.find(T.departDate)
    val dTimeCol = keys.find(T.departTime)
    val aDateCol = keys.find(T.arriveDate)
    val aTimeCol = keys.find(T.arriveTime)
    val noteCol = keys.find(T.note)
    var last = r
    for (row in r + 1 until s.rows.size) {
        if (s.rows[row].all { it.isEmpty() }) continue
        last = row
        fun at(c: Int?) = c?.let { s.cell(row, it) }.orEmpty()
        val no = FLIGHT_NO.find(at(noCol).uppercase())?.groupValues?.get(1)?.replace(" ", "")
        val from = AIRPORT.find(at(fromCol).uppercase())?.groupValues?.get(1)
        val to = AIRPORT.find(at(toCol).uppercase())?.groupValues?.get(1)
        val dDate = parseSheetDate(at(dDateCol), ctx.tripStart)
        val dTime = splitTime(at(dTimeCol)).time
        val label = at(noCol).ifEmpty { "${at(fromCol)} → ${at(toCol)}" }
        val reason = when {
            no == null -> "沒有航班號碼"
            from == null || to == null -> "機場要寫三碼代碼（例如 TPE）"
            dDate == null -> "出發日期看不懂"
            dTime == null -> "出發時間看不懂"
            else -> null
        }
        if (reason != null) { out.skipped += Skipped("航班", label, reason); continue }
        out.flights += FlightRow(
            no!!, from!!, to!!, dDate!!, dTime!!,
            airline = at(airlineCol).ifEmpty { null },
            arriveDate = parseSheetDate(at(aDateCol), ctx.tripStart),
            arriveTime = splitTime(at(aTimeCol)).time,
            note = at(noteCol).ifEmpty { null },
        )
    }
    return last
}

private fun checklistTable(s: Section, r: Int, keys: List<String>, kind: ChecklistKind?, out: TravelOut): Int {
    val listCol = keys.find(T.list)
    val entryCol = keys.find(T.entry)!!
    var last = r
    for (row in r + 1 until s.rows.size) {
        val text = s.cell(row, entryCol)
        if (text.isEmpty()) continue
        last = row
        val k = kind ?: listCol?.let { checklistKindOf(s.cell(row, it)) } ?: ChecklistKind.todo
        out.checklist += ChecklistRow(k, text.take(100))
    }
    return last
}

/** Airports people write by name in Chinese, to their codes. */
private val AIRPORT_NAMES = listOf(
    "桃園" to "TPE", "松山" to "TSA", "小港" to "KHH", "高雄" to "KHH", "清泉崗" to "RMQ",
    "成田" to "NRT", "羽田" to "HND", "關西" to "KIX", "關空" to "KIX", "中部" to "NGO", "新千歲" to "CTS",
    "福岡" to "FUK", "那霸" to "OKA", "熊本" to "KMJ", "廣島" to "HIJ", "仙台" to "SDJ", "岡山" to "OKJ",
    "仁川" to "ICN", "金浦" to "GMP", "金海" to "PUS", "大邱" to "TAE", "濟州" to "CJU",
    "素萬那普" to "BKK", "廊曼" to "DMK", "清邁" to "CNX", "香港" to "HKG", "澳門" to "MFM", "樟宜" to "SIN",
    "墨爾本" to "MEL", "雪梨" to "SYD", "布達佩斯" to "BUD",
)
private val DAY_FLIGHT_NO = Regex("""(?<![A-Za-z0-9])([A-Z][A-Z0-9]|[0-9][A-Z])\s?(\d{2,4})(?![0-9])""")
private val CLOCK = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")

/**
 * A day cell that is really a flight: a flight number and two airports,
 * like "08:10 桃園 - 12:20成田 XX628". Anything less stays a stop.
 */
internal fun dayFlight(item: ItemRow): FlightRow? {
    val time = item.time ?: return null
    val text = item.title + (item.note?.let { "\n$it" } ?: "")
    val no = DAY_FLIGHT_NO.find(text) ?: return null
    val stripped = text.removeRange(no.range.first, no.range.last + 1)
    val found = (AIRPORT_NAMES.flatMap { (name, code) -> Regex(Regex.escape(name)).findAll(stripped).map { it.range.first to code }.toList() } +
        AIRPORT.findAll(stripped).map { it.range.first to it.groupValues[1] })
        .sortedBy { it.first }.map { it.second }.distinct()
    if (found.size != 2) return null
    val arrive = CLOCK.find(stripped)?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" }
    val nextDay = stripped.contains("+1")
    return FlightRow(
        flightNo = no.groupValues[1] + no.groupValues[2],
        fromCode = found[0],
        toCode = found[1],
        departDate = item.date,
        departTime = time,
        arriveDate = arrive?.let { if (nextDay) nextDay(item.date) else item.date },
        arriveTime = arrive,
    )
}
