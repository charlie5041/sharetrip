package tw.tripshare.data

import kotlinx.coroutines.withTimeoutOrNull
import tw.tripshare.core.sheet.SheetError
import tw.tripshare.core.sheet.SheetInfo
import tw.tripshare.core.sheet.explainSheetError
import tw.tripshare.core.sheet.parseTabs
import tw.tripshare.core.sheet.parseValues
import tw.tripshare.core.sheet.tabsUrl
import tw.tripshare.core.sheet.valuesUrl
import tw.tripshare.httpGetResponse

/**
 * Reads Google Sheets shared as "anyone with the link can view", with the
 * Firebase browser key (Google Sheets API enabled in project tripshare-f6209).
 * The key is public anyway: it sits in every web build. Throws [SheetError]
 * with a message for the person.
 */
object GoogleSheets {
    private const val KEY = "AIzaSyCUpTpd83q0uwAXZSd62i7-u5eA13Uuz1E"

    private suspend fun get(url: String): String {
        val (status, body) = withTimeoutOrNull(20_000) { httpGetResponse(url) }
            ?: throw SheetError("連不上 Google，請確認網路")
        if (status !in 200..299) throw SheetError(explainSheetError(status, body))
        return body
    }

    suspend fun tabs(id: String): SheetInfo = parseTabs(get(tabsUrl(id, KEY)))

    suspend fun values(id: String, title: String): List<List<String>> = parseValues(get(valuesUrl(id, title, KEY)))
}
