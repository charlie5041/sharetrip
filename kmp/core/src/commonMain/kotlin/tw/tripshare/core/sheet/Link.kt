package tw.tripshare.core.sheet

import tw.tripshare.core.encodeQuery

/**
 * Reading a Google Sheet shared as "anyone with the link can view", through
 * the Sheets API with the app's browser key. The API answers JSON, read
 * here with a small parser rather than a library that would add to the web
 * download.
 */
data class SheetRef(val id: String, val gid: Long?)

data class SheetTab(val title: String, val gid: Long)

private val SHEET_URL = Regex("""docs\.google\.com/spreadsheets/(?:u/\d+/)?d/([A-Za-z0-9_-]{20,})""")
private val GID = Regex("""[#&?]gid=(\d+)""")

/** The spreadsheet in a pasted link, or null when it isn't one. */
fun parseSheetLink(text: String): SheetRef? {
    val m = SHEET_URL.find(text.trim()) ?: return null
    return SheetRef(m.groupValues[1], GID.find(text)?.groupValues?.get(1)?.toLongOrNull())
}

fun tabsUrl(id: String, key: String) =
    "https://sheets.googleapis.com/v4/spreadsheets/$id?fields=${encodeQuery("properties.title,sheets.properties(title,sheetId,hidden)")}&key=$key"

/** All of one tab, as displayed (dates and currency formats included). */
fun valuesUrl(id: String, title: String, key: String) =
    "https://sheets.googleapis.com/v4/spreadsheets/$id/values/${encodeQuery("'" + title.replace("'", "''") + "'")}" +
        "?valueRenderOption=FORMATTED_VALUE&majorDimension=ROWS&key=$key"

data class SheetInfo(val title: String, val tabs: List<SheetTab>)

/** The spreadsheet's name and visible tabs, from [tabsUrl]'s answer. */
fun parseTabs(json: String): SheetInfo {
    val root = parseJson(json) as? Map<*, *> ?: throw SheetError("讀不懂 Google 的回應")
    val title = ((root["properties"] as? Map<*, *>)?.get("title") as? String).orEmpty()
    val tabs = (root["sheets"] as? List<*>).orEmpty().mapNotNull { s ->
        val p = (s as? Map<*, *>)?.get("properties") as? Map<*, *> ?: return@mapNotNull null
        if (p["hidden"] == true) return@mapNotNull null
        SheetTab(p["title"] as? String ?: return@mapNotNull null, (p["sheetId"] as? Double)?.toLong() ?: 0)
    }
    return SheetInfo(title, tabs)
}

/** One tab's cells, from [valuesUrl]'s answer. */
fun parseValues(json: String): List<List<String>> {
    val root = parseJson(json) as? Map<*, *> ?: throw SheetError("讀不懂 Google 的回應")
    return (root["values"] as? List<*>).orEmpty().map { row ->
        (row as? List<*>).orEmpty().map { cell ->
            when (cell) {
                is String -> cell
                is Double -> if (cell == kotlin.math.floor(cell)) cell.toLong().toString() else cell.toString()
                null -> ""
                else -> cell.toString()
            }
        }
    }
}

class SheetError(message: String) : Exception(message)

/** What to tell the person when Google refused, from the HTTP status and body. */
fun explainSheetError(status: Int, body: String): String {
    val message = ((parseJsonOrNull(body) as? Map<*, *>)?.get("error") as? Map<*, *>)?.get("message") as? String ?: ""
    return when {
        message.contains("has not been used") || message.contains("is disabled") || message.contains("blocked") ->
            "App 還不能讀試算表（Google Sheets API 尚未開啟），請跟 Charlie 說"
        status == 403 -> "這份試算表沒有公開。請在試算表按「共用」，把一般存取權改成「知道連結的任何人」都能檢視"
        status == 404 -> "找不到這份試算表，請確認連結"
        status == 400 && message.contains("parse range") -> "找不到這個分頁"
        status == 429 -> "讀太多次了，請過一分鐘再試"
        else -> "讀取失敗（$status）${if (message.isNotEmpty()) "：$message" else ""}"
    }
}

/** Reads [rows] (one tab) like a paste. */
fun analyzeRows(rows: List<List<String>>, ctx: ImportContext, tabTitle: String? = null): ImportResult =
    analyzeSections(listOf(Section(tabTitle, rows.map { r -> r.map { it.trim() } })), ctx)

// ---- A small JSON reader ---------------------------------------------------

fun parseJsonOrNull(text: String): Any? = runCatching { parseJson(text) }.getOrNull()

/** Objects become maps, arrays lists, numbers Double. Throws [SheetError] on malformed input. */
fun parseJson(text: String): Any? {
    val p = JsonReader(text)
    val v = p.value()
    p.skipSpace()
    if (p.i != text.length) throw SheetError("JSON 後面還有多餘的內容")
    return v
}

private class JsonReader(val s: String) {
    var i = 0

    fun skipSpace() { while (i < s.length && s[i].isWhitespace()) i++ }

    private fun fail(): Nothing = throw SheetError("讀不懂 Google 的回應")

    fun value(): Any? {
        skipSpace()
        if (i >= s.length) fail()
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> word("true", true)
            'f' -> word("false", false)
            'n' -> word("null", null)
            else -> if (c == '-' || c.isDigit()) num() else fail()
        }
    }

    private fun word(w: String, v: Any?): Any? {
        if (!s.startsWith(w, i)) fail()
        i += w.length
        return v
    }

    private fun num(): Double {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        return s.substring(start, i).toDoubleOrNull() ?: fail()
    }

    private fun str(): String {
        i++ // opening quote
        val b = StringBuilder()
        while (true) {
            if (i >= s.length) fail()
            val c = s[i++]
            when (c) {
                '"' -> return b.toString()
                '\\' -> {
                    if (i >= s.length) fail()
                    when (val e = s[i++]) {
                        'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r')
                        'b' -> b.append('\b'); 'f' -> b.append('\u000C')
                        'u' -> {
                            if (i + 4 > s.length) fail()
                            b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail())
                            i += 4
                        }
                        else -> b.append(e)
                    }
                }
                else -> b.append(c)
            }
        }
    }

    private fun arr(): List<Any?> {
        i++
        val out = mutableListOf<Any?>()
        skipSpace()
        if (i < s.length && s[i] == ']') { i++; return out }
        while (true) {
            out += value()
            skipSpace()
            if (i >= s.length) fail()
            when (s[i++]) { ',' -> continue; ']' -> return out; else -> fail() }
        }
    }

    private fun obj(): Map<String, Any?> {
        i++
        val out = LinkedHashMap<String, Any?>()
        skipSpace()
        if (i < s.length && s[i] == '}') { i++; return out }
        while (true) {
            skipSpace()
            if (i >= s.length || s[i] != '"') fail()
            val k = str()
            skipSpace()
            if (i >= s.length || s[i++] != ':') fail()
            out[k] = value()
            skipSpace()
            if (i >= s.length) fail()
            when (s[i++]) { ',' -> continue; '}' -> return out; else -> fail() }
        }
    }
}
