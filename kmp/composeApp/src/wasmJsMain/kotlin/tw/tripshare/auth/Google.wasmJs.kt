@file:OptIn(ExperimentalWasmJsInterop::class)

package tw.tripshare.data

import kotlinx.coroutines.await
import tw.tripshare.auth.GoogleAuthProvider
import tw.tripshare.auth.getAuth
import tw.tripshare.auth.linkWithPopup
import tw.tripshare.auth.signInWithPopup
import kotlin.js.Promise

private fun selectAccount(): JsAny = js("({ prompt: 'select_account' })")

/** "ok", or "error code|email|message", so a rejection never crosses into Kotlin as an exception. */
private fun settle(p: Promise<JsAny?>): Promise<JsString> = js("""p.then(() => 'ok', (e) =>
  'error ' + (e && e.code || 'unknown') + '|' + (e && e.customData && e.customData.email || '') + '|' + (e && e.message || ''))""")

private fun provider() = GoogleAuthProvider().also { it.setCustomParameters(selectAccount()) }

private fun outcome(started: Promise<JsAny?>): suspend () -> GoogleResult {
    val settled = settle(started)
    return {
        val r = settled.await<JsString>().toString()
        if (r == "ok") {
            GoogleResult.Ok
        } else {
            val (code, email, message) = r.removePrefix("error ").split('|', limit = 3).let { Triple(it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
            when (code.removePrefix("auth/")) {
                "credential-already-in-use", "email-already-in-use" -> GoogleResult.InUse(email.ifEmpty { null })
                "popup-closed-by-user", "cancelled-popup-request" -> GoogleResult.Cancelled
                else -> GoogleResult.Failed(googleErrorText(code, message))
            }
        }
    }
}

actual fun platformGoogleLink(): suspend () -> GoogleResult {
    val user = getAuth().currentUser ?: return { GoogleResult.Failed("還沒登入，請重新整理後再試") }
    return outcome(linkWithPopup(user, provider()))
}

actual fun platformGoogleSignIn(): suspend () -> GoogleResult = outcome(signInWithPopup(getAuth(), provider()))
