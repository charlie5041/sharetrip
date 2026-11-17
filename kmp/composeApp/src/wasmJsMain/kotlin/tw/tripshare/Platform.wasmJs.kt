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

private fun jsPlatform(): String = js("""(() => {
  const ua = navigator.userAgent;
  const ios = /iPhone|iPad|iPod/.test(ua) || (ua.includes('Macintosh') && navigator.maxTouchPoints > 1);
  return 'web-' + (ios ? 'iOS' : ua.includes('Android') ? 'android' : 'desktop');
})()""")

/** index.html sends errors here once the app is running (before that it shows them on screen). */
private fun jsOnError(cb: (String, String) -> Unit): Unit = js("{ window.tripshareOnError = (m, s) => cb(String(m), String(s || '')); }")

actual fun platformName(): String = jsPlatform()

actual fun installCrashHook(onError: (String, String?) -> Unit) = jsOnError { m, s -> onError(m, s.ifEmpty { null }) }

private fun jsStandalone(): Boolean =
    js("window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true")

private fun jsAndroid(): Boolean = js("navigator.userAgent.includes('Android')")

private fun jsInApp(): String = js("""(() => {
  const ua = navigator.userAgent;
  return ua.includes(' Line/') ? 'LINE' : /FBAN|FBAV|FB_IAB/.test(ua) ? 'Facebook'
    : ua.includes('Instagram') ? 'Instagram' : ua.includes('MicroMessenger') ? '微信' : '';
})()""")

private fun jsCanPrompt(): Boolean = js("!!window.tripshareInstall")

private fun jsPrompt(): Promise<JsBoolean> = js("""(async () => {
  const e = window.tripshareInstall;
  if (!e) return false;
  await e.prompt();
  const c = await e.userChoice;
  return c.outcome === 'accepted';
})().catch(() => false)""")

private fun jsOrigin(): String = js("location.origin + '/'")

private fun jsOpenExternal(): Unit = js("""{
  const u = new URL(location.href);
  u.searchParams.set('openExternalBrowser', '1');
  location.href = u.toString();
}""")

private fun jsLoadFlag(key: String): Boolean = js("(() => { try { return localStorage.getItem(key) === '1'; } catch (_) { return false; } })()")

private fun jsSaveFlag(key: String, value: Boolean): Unit = js("{ try { localStorage.setItem(key, value ? '1' : '0'); } catch (_) {} }")

actual fun installEnv(): InstallEnv? {
    val p = jsPlatform()
    return InstallEnv(
        standalone = jsStandalone(),
        platform = if (p == "web-iOS") "ios" else if (jsAndroid()) "android" else "other",
        inAppBrowser = jsInApp().ifEmpty { null },
        canPrompt = jsCanPrompt(),
        link = jsOrigin(),
    )
}

actual suspend fun promptInstall(): Boolean = runCatching { jsPrompt().await<JsBoolean>().toBoolean() }.getOrDefault(false)

actual fun openInExternalBrowser() = jsOpenExternal()

actual fun loadFlag(key: String): Boolean = jsLoadFlag(key)

actual fun saveFlag(key: String, value: Boolean) = jsSaveFlag(key, value)
