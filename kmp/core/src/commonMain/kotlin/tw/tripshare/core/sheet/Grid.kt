package tw.tripshare.core.sheet

/**
 * Text pasted from a spreadsheet, cut into grids of cells.
 *
 * Copying a range in Google Sheets or Excel puts tab-separated text on the
 * clipboard; a cell holding a line break or a quote comes wrapped in quotes
 * with inner quotes doubled. An AI chat answers with Markdown tables
 * instead, often several under "## heading" lines. Both are read here.
 */
data class Section(
    /** The "## 花費" heading above the table, or null. */
    val title: String?,
    val rows: List<List<String>>,
) {
    fun cell(r: Int, c: Int): String = rows.getOrNull(r)?.getOrNull(c).orEmpty()
    val width: Int get() = rows.maxOfOrNull { it.size } ?: 0
}

private val HEADING = Regex("""^\s*(?:#{1,6}\s*(.+?)\s*#*|【(.+?)】)\s*$""")
private val MD_SEPARATOR = Regex("""^\s*\|?[\s:|-]+\|?\s*$""")

fun parsePasted(text: String): List<Section> {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val out = mutableListOf<Section>()
    var title: String? = null
    val chunk = mutableListOf<String>()

    fun flush() {
        val body = chunk.filterNot { it.trim().startsWith("```") }
        chunk.clear()
        val nonBlank = body.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return
        val markdown = nonBlank.count { it.trim().startsWith("|") } * 2 > nonBlank.size
        val rows = if (markdown) markdownRows(body) else tsvRows(body.joinToString("\n"))
        val cleaned = rows.map { r -> r.map(::cleanCell) }.dropLastWhile { r -> r.all { it.isEmpty() } }
        if (cleaned.any { r -> r.any { it.isNotEmpty() } }) out += Section(title, cleaned)
    }

    for (line in lines) {
        val h = if ('\t' in line || line.trim().startsWith("|")) null else HEADING.matchEntire(line)
        if (h != null) {
            flush()
            title = (h.groupValues[1].ifEmpty { h.groupValues[2] }).trim()
        } else {
            chunk += line
        }
    }
    flush()
    return out
}

private fun markdownRows(lines: List<String>): List<List<String>> =
    lines.map { it.trim() }
        .filter { it.startsWith("|") && !MD_SEPARATOR.matches(it) }
        .map { line ->
            val inner = line.removePrefix("|").let { if (it.endsWith("|")) it.dropLast(1) else it }
            inner.split('|').map { it.trim().replace("<br>", "\n").replace("<br/>", "\n") }
        }

/** Tab-separated values with spreadsheet quoting. */
internal fun tsvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var i = 0
    var atFieldStart = true
    var quoted = false
    while (i < text.length) {
        val ch = text[i]
        if (quoted) {
            if (ch == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
            } else field.append(ch)
        } else when {
            ch == '"' && atFieldStart -> { quoted = true; atFieldStart = false }
            ch == '\t' -> { row += field.toString(); field.clear(); atFieldStart = true }
            ch == '\n' -> {
                row += field.toString(); field.clear(); rows += row; row = mutableListOf(); atFieldStart = true
            }
            else -> { field.append(ch); atFieldStart = false }
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
    return rows
}

private fun cleanCell(s: String): String =
    s.replace(' ', ' ').lines().joinToString("\n") { it.trim() }.trim('\n', ' ')

/** A header cell reduced for matching: no spaces, emoji, brackets or case. */
internal fun headerKey(s: String): String =
    s.replace(Regex("""[（(\[【].*?[)）\]】]"""), "")
        .filter { it.isLetterOrDigit() || it == '$' }
        .lowercase()

/** What is inside brackets in a header, e.g. "金額（日圓）" → "日圓". */
internal fun headerBracket(s: String): String? =
    Regex("""[（(\[【](.*?)[)）\]】]""").find(s)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
