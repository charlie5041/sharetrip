package tw.tripshare.core

/**
 * A link that opens [place] in Google Maps (the app when installed, the
 * browser otherwise), unless a specific [mapUrl] was saved. Only http(s)
 * links are opened, so a pasted "javascript:" can't run anything.
 */
fun mapLink(place: String?, mapUrl: String?): String? {
    val url = mapUrl?.trim().orEmpty()
    if (url.isNotEmpty()) {
        val ok = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
        return if (ok) url else null
    }
    val q = place?.trim().orEmpty()
    if (q.isEmpty()) return null
    return "https://www.google.com/maps/search/?api=1&query=${encodeQuery(q)}"
}

/** Percent-encodes [s] as UTF-8 for a URL query value. */
fun encodeQuery(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xff
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-_.~") append(ch)
        else append('%').append(HEX[c shr 4]).append(HEX[c and 15])
    }
}

private const val HEX = "0123456789ABCDEF"

/** The trimmed text, or null when nothing was typed. */
fun String.orNullIfBlank(): String? = trim().ifEmpty { null }
