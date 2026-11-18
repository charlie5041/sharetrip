package tw.tripshare.core.sheet

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import tw.tripshare.core.Currency
import tw.tripshare.core.ExpenseCategory
import tw.tripshare.core.Transport
import tw.tripshare.core.hm
import tw.tripshare.core.parseYmd
import tw.tripshare.core.ymd
import kotlin.math.abs

// ---- Currencies ------------------------------------------------------------

/** Ways people write a currency, longest first so "nt$" wins over "$". */
private val CURRENCY_WORDS: List<Pair<String, String>> = listOf(
    "TWD" to listOf("nt$", "ntd", "twd", "nt", "新台幣", "新臺幣", "台幣", "臺幣"),
    "JPY" to listOf("jpy", "yen", "¥", "￥", "円", "日圓", "日幣", "日元", "日円"),
    "KRW" to listOf("krw", "won", "₩", "￦", "韓元", "韓幣", "韓圜", "韓圓"),
    "EUR" to listOf("eur", "€", "歐元", "歐"),
    "HUF" to listOf("huf", "ft", "福林"),
    "CZK" to listOf("czk", "kč", "kc", "克朗"),
    "THB" to listOf("thb", "฿", "泰銖", "泰幣"),
    "AUD" to listOf("aud", "au$", "a$", "澳幣", "澳元"),
    "USD" to listOf("usd", "us$", "美元", "美金"),
    "HKD" to listOf("hkd", "hk$", "港幣", "港元"),
    "CNY" to listOf("cny", "rmb", "人民幣"),
    "SGD" to listOf("sgd", "s$", "新加坡幣", "新幣"),
    "VND" to listOf("vnd", "₫", "越南盾"),
    "GBP" to listOf("gbp", "£", "英鎊"),
    "MYR" to listOf("myr", "rm", "令吉", "馬幣"),
    "PHP" to listOf("php", "₱", "披索"),
    "IDR" to listOf("idr", "rp", "印尼盾"),
    "NZD" to listOf("nzd", "nz$", "紐幣"),
    "CAD" to listOf("cad", "c$", "加幣"),
    "CHF" to listOf("chf", "瑞士法郎"),
).flatMap { (code, words) -> words.map { it to code } }.sortedByDescending { it.first.length }

/** "日圓", "JPY", "¥" → "JPY"; null when it names no currency we know. */
fun currencyOf(text: String): String? {
    val t = text.trim().lowercase().replace(" ", "")
    if (t.isEmpty()) return null
    Currency.all.firstOrNull { it.code.lowercase() == t }?.let { return it.code }
    return CURRENCY_WORDS.firstOrNull { (w, _) -> t == w }?.second
}

/** A currency word somewhere inside [text], e.g. "NT$1,207" or "1000克朗". */
private fun currencyIn(text: String): String? {
    val t = text.lowercase().replace(" ", "")
    if (t.isEmpty()) return null
    // Short Latin words like "ft" or "rm" only count as a whole word.
    return CURRENCY_WORDS.firstOrNull { (w, _) ->
        val i = t.indexOf(w)
        i >= 0 && (w.any { !it.isLetter() || it.code > 127 } || w.length >= 3 ||
            ((i == 0 || !t[i - 1].isLetter()) && (i + w.length == t.length || !t[i + w.length].isLetter())))
    }?.second
}

/** An amount as written in a sheet, read but not yet tied to a currency's decimals. */
data class Money(
    /** Digits with an optional ".fraction", never negative. */
    val number: String,
    /** The currency the text itself names, if any. */
    val currency: String?,
)

class MoneyError(message: String) : Exception(message)

private val NUMBER = Regex("""\d+(?:\.\d+)?""")

/** "NT$2,617", "₩108,912", "€27.90", "1000克朗", "4,886" → a [Money]; throws [MoneyError] with the reason. */
fun parseMoney(raw: String): Money {
    val t = toHalfWidth(raw).replace(",", "").replace(" ", "").trim()
    if (t.isEmpty()) throw MoneyError("沒有金額")
    val numbers = NUMBER.findAll(t).toList()
    if (numbers.isEmpty()) throw MoneyError("看不出金額")
    if (numbers.size > 1) {
        throw MoneyError(if (Regex("""\d\s*[-~～–]\s*\d""").containsMatchIn(t)) "金額是一個範圍" else "一格裡有好幾個數字")
    }
    val n = numbers.single()
    val before = t.substring(0, n.range.first)
    if (before.startsWith("-") || before.startsWith("−") || before.endsWith("-") || before.endsWith("−")) throw MoneyError("負數金額不能匯入")
    val rest = before + t.substring(n.range.last + 1)
    return Money(n.value, currencyIn(rest))
}

/** [number] in [currency]'s minor units, rounding half up when it has more decimals than the currency uses. */
fun toMinor(number: String, currency: Currency): Pair<Long, Boolean> {
    val whole = number.substringBefore('.')
    val frac = number.substringAfter('.', "")
    val kept = frac.take(currency.decimals).padEnd(currency.decimals, '0')
    var minor = (whole + kept).toLong()
    val dropped = frac.drop(currency.decimals)
    val rounded = dropped.any { it != '0' }
    if (dropped.isNotEmpty() && dropped[0] >= '5') minor += 1
    return minor to rounded
}

private fun toHalfWidth(s: String): String = buildString {
    for (ch in s) append(if (ch in '０'..'９') '0' + (ch - '０') else if (ch == '．') '.' else if (ch == '，') ',' else ch)
}

// ---- Dates and times -------------------------------------------------------

private val YMD = Regex("""^\s*(\d{4})\s*[/\-.年]\s*(\d{1,2})\s*[/\-.月]\s*(\d{1,2})""")
private val MD = Regex("""^\s*(\d{1,2})\s*[/月]\s*(\d{1,2})(?!\d)(?!\s*[:：])""")
private val YYYYMMDD = Regex("""^\s*(20\d{2})(\d{2})(\d{2})(?!\d)""")

/**
 * The date a cell starts with: "2025/04/30 (三)", "11/4 (Wed)", "6/1 Day1",
 * "2026-11-04". Without a year, the year that puts it nearest the trip.
 */
fun parseSheetDate(text: String, tripStart: String): String? {
    val start = parseYmd(tripStart)
    YMD.find(text)?.let { m -> return date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
    YYYYMMDD.find(text)?.let { m -> return date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
    val m = MD.find(text) ?: return null
    val month = m.groupValues[1].toInt()
    val day = m.groupValues[2].toInt()
    return (start.year - 1..start.year + 1)
        .mapNotNull { y -> date(y, month, day)?.let { it to abs(start.daysUntil(parseYmd(it))) } }
        .minByOrNull { it.second }?.first
}

private fun date(y: Int, m: Int, d: Int): String? = runCatching { ymd(LocalDate(y, m, d)) }.getOrNull()

private val LEADING_TIME = Regex("""^\s*(\d{1,2})\s*[:：]\s*(\d{2})""")
private val TIME_RANGE_TAIL = Regex("""^\s*[-~～–—]\s*(\d{1,2}\s*[:：]\s*\d{2})(\s*\(\+1\))?""")

/** A cell split into its leading time of day ("17:15"), any end time, and what follows. */
data class Timed(val time: String?, val until: String?, val rest: String)

fun splitTime(text: String): Timed {
    val m = LEADING_TIME.find(text) ?: return Timed(null, null, text.trim())
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (h !in 0..23 || min !in 0..59) return Timed(null, null, text.trim())
    var rest = text.substring(m.range.last + 1)
    var until: String? = null
    TIME_RANGE_TAIL.find(rest)?.let { r ->
        until = r.groupValues[1].replace(" ", "").replace('：', ':') + r.groupValues[2].trim()
        rest = rest.substring(r.range.last + 1)
    }
    return Timed(hm(h, min), until, rest.trimStart(' ', '-', '~', '～', '–', '　').trim())
}

// ---- Guesses from words ----------------------------------------------------

private fun hasAny(text: String, words: List<String>) = words.any { text.contains(it) }

/** The category an expense's name suggests. */
fun guessCategory(text: String): ExpenseCategory {
    val t = text.lowercase()
    return when {
        Regex(""".+到.+""").matches(t.trim()) && !hasAny(t, listOf("門票", "住宿")) -> ExpenseCategory.transport
        hasAny(t, listOf("住宿", "飯店", "酒店", "旅館", "民宿", "旅店", "hotel", "inn", "hostel", "airbnb", "韓屋", "resort", "residence", "check in", "(bk)", "bk ", "booking", "agoda", "(trip)")) -> ExpenseCategory.lodging
        hasAny(t, listOf("纜車", "門票", "入場", "周遊", "pass", "樂園", "遊船", "遊覽船", "博物館", "美術館", "水族館", "展覽", "tour", "spa", "體驗")) -> ExpenseCategory.ticket
        hasAny(t, listOf("機票", "航班", "機場", "車", "計程", "taxi", "uber", "grab", "地鐵", "捷運", "機捷", "jr", "ktx", "srt", "新幹線", "巴士", "bus", "雙條", "etc", "快綫", "快線", "火車", "train", "渡輪", "加油", "停車", "高速", "交通", "西鐵", "rental", "suica", "icoca", "t-money", "悠遊")) -> ExpenseCategory.transport
        hasAny(t, listOf("票")) -> ExpenseCategory.ticket
        // What was bought outweighs where: "Lawson * 戶外品牌 外套", "Big Camera 玩具".
        hasAny(t, listOf("外套", "衣服", "上衣", "鞋", "褲", "帽", "襪", "手套", "雨傘", "玩具", "泡澡", "藥妝", "化妝", "伴手禮", "紀念品", "吊飾", "免稅", "代購",
            "big camera", "bic camera", "唐吉", "驚安", "donki", "uniqlo", "workman", "olive young", "outlet")) -> ExpenseCategory.shopping
        hasAny(t, listOf("餐", "食", "飯", "麵", "面", "咖啡", "cafe", "café", "coffee", "早午", "拉麵", "燒肉", "烤", "串", "湯", "甜", "麵包", "酒", "bar", "7-11", "711", "超商", "便利", "全家", "lawson", "小吃", "夜市", "壽司", "披薩", "pizza", "茶", "點心", "飲料", "雞", "菜", "奶", "冰", "米線", "香腸", "餅", "牛", "豬", "肉", "丼", "定食", "料理", "食堂", "超市", "水果", "shake shack", "早餐", "宵夜")) -> ExpenseCategory.food
        hasAny(t, listOf("購物", "伴手禮", "紀念品", "衣", "鞋", "外套", "outlet", "藥妝", "淘寶", "olive", "免稅", "代購", "uniqlo", "spao", "化妝", "百貨", "褲", "包", "土產", "吊飾", "雜貨", "workman", "big camera", "唐吉")) -> ExpenseCategory.shopping
        else -> ExpenseCategory.other
    }
}

/** The category a "分類" cell names, by label ("餐飲") or name ("food"). */
fun categoryOf(text: String): ExpenseCategory? {
    val t = text.trim().lowercase()
    if (t.isEmpty()) return null
    return ExpenseCategory.entries.firstOrNull { it.label == t || it.name == t }
        ?: when (t) { "吃", "食", "餐", "美食" -> ExpenseCategory.food; "車資", "車" -> ExpenseCategory.transport; "飯店" -> ExpenseCategory.lodging; else -> null }
}

private val FLIGHT_NO_IN = Regex("""(?<![A-Za-z0-9])[A-Z]{2}\s?\d{2,4}(?![0-9])""")

/** How a line of the itinerary says you get there: its emoji or words. */
fun guessTransport(text: String): Transport {
    val t = text.lowercase()
    return when {
        hasAny(t, listOf("✈", "🛫", "🛬", "航班", "班機", "飛往")) || FLIGHT_NO_IN.containsMatchIn(text) -> Transport.flight
        hasAny(t, listOf("⛴", "🚢", "🛳", "渡輪", "搭船", "遊船")) -> Transport.ferry
        hasAny(t, listOf("🚃", "🚂", "🚄", "🚅", "🚆", "🚇", "🚉", "🚈", "電車", "火車", "地鐵", "捷運", "新幹線", "ktx", "srt", "jr ", "jr線", "特快", "快速", "nex", "haruka")) -> Transport.train
        hasAny(t, listOf("🚌", "🚍", "巴士", "公車", "bus")) -> Transport.bus
        hasAny(t, listOf("🚕", "🚖", "計程車", "taxi", "uber", "grab", "叫車")) -> Transport.taxi
        hasAny(t, listOf("🚗", "🚙", "🏍", "租車", "取車", "還車", "開車", "自駕", "road trip", "重機", "摩托車")) -> Transport.car
        hasAny(t, listOf("🚶", "步行", "走路")) -> Transport.walk
        else -> Transport.none
    }
}

/** A "交通" cell: a label ("電車") or words that suggest one. */
fun transportOf(text: String): Transport {
    val t = text.trim()
    if (t.isEmpty()) return Transport.none
    return Transport.entries.firstOrNull { it.label == t || it.name == t.lowercase() } ?: guessTransport(t)
}
