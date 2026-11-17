@file:OptIn(ExperimentalWasmJsInterop::class)

package tw.tripshare

import dev.gitlive.firebase.FirebaseOptions
import kotlinx.coroutines.await
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.js.Promise

actual fun platformContext(): Any? = null

actual val isWeb: Boolean = true

private fun jsNow(): Double = js("Date.now()")
private fun jsYear(): Int = js("new Date().getFullYear()")
private fun jsMonth(): Int = js("new Date().getMonth() + 1")
private fun jsDay(): Int = js("new Date().getDate()")
private fun jsRandomByte(): Int = js("crypto.getRandomValues(new Uint8Array(1))[0]")
private fun jsCopy(text: String): Unit = js("navigator.clipboard && navigator.clipboard.writeText(text)")

actual fun nowMillis(): Long = jsNow().toLong()

actual fun today(): LocalDate = LocalDate(jsYear(), jsMonth(), jsDay())

private fun jsParts(ms: Double): String =
    js("(() => { const d = new Date(ms); return [d.getFullYear(), d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes()].join(','); })()")

private fun jsLocalMs(y: Int, mo: Int, d: Int, h: Int, mi: Int): Double = js("new Date(y, mo - 1, d, h, mi).getTime()")

private fun jsFetchText(url: String): Promise<JsString?> =
    js("fetch(url).then(r => r.ok ? r.text() : null).catch(() => null)")

actual fun localDateTime(millis: Long): LocalDateTime {
    val p = jsParts(millis.toDouble()).split(',').map { it.toInt() }
    return LocalDateTime(p[0], p[1], p[2], p[3], p[4])
}

actual fun localMillis(dt: LocalDateTime): Long =
    jsLocalMs(dt.year, dt.month.ordinal + 1, dt.day, dt.hour, dt.minute).toLong()

actual suspend fun httpGetText(url: String): String? = runCatching { jsFetchText(url).await<JsString?>()?.toString() }.getOrNull()

actual fun secureRandomBytes(n: Int): ByteArray = ByteArray(n) { jsRandomByte().toByte() }

actual fun copyToClipboard(text: String) = jsCopy(text)

actual val firebaseOptions = FirebaseOptions(
    applicationId = "1:307482465264:web:4ff23232bc0db95e3d0335",
    apiKey = "AIzaSyCUpTpd83q0uwAXZSd62i7-u5eA13Uuz1E",
    projectId = "tripshare-f6209",
    authDomain = "tripshare-f6209.firebaseapp.com",
    storageBucket = "tripshare-f6209.firebasestorage.app",
    gcmSenderId = "307482465264",
)

private fun jsOpen(url: String): Unit = js("void window.open(url, '_blank', 'noopener')")

actual fun openUrl(url: String) = jsOpen(url)
