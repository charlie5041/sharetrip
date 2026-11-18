package tw.tripshare.data

import kotlinx.coroutines.withTimeoutOrNull
import tw.tripshare.core.formatRate
import tw.tripshare.httpGetText

/**
 * Today's reference rate, as a starting point the person can overwrite with
 * what their card actually charged. Uses open.er-api.com's free endpoint,
 * which needs no key and updates daily; same source as the Flutter app.
 */
class ExchangeRates {
    private val cache = mutableMapOf<String, String>()

    /** One unit of [from] in [to] as a decimal string, or null when offline or unknown. */
    suspend fun rate(from: String, to: String): String? {
        if (from == to) return "1"
        val body = cache[from] ?: withTimeoutOrNull(8000) {
            httpGetText("https://open.er-api.com/v6/latest/$from")
        }?.takeIf { SUCCESS.containsMatchIn(it) }?.also { cache[from] = it } ?: return null
        // A regex rather than a JSON library, which would add to the web download.
        val rates = body.substringAfter("\"rates\"", "")
        val value = Regex(""""$to"\s*:\s*([0-9.eE+-]+)""").find(rates)?.groupValues?.get(1)?.toDoubleOrNull()
        return value?.let(::formatRate)
    }

    private companion object {
        val SUCCESS = Regex(""""result"\s*:\s*"success"""")
    }
}
