@file:OptIn(ExperimentalWasmJsInterop::class)

package tw.tripshare.data

import kotlinx.coroutines.await
import tw.tripshare.auth.GoogleAuthProvider
import tw.tripshare.auth.getAuth
import tw.tripshare.auth.linkWithPopup
import tw.tripshare.auth.signInWithCredential
import tw.tripshare.auth.signInWithPopup
import kotlin.js.Promise

private fun selectAccount(): JsAny = js("({ prompt: 'select_account' })")

/** Null when the step worked, else the error, so a rejection never crosses into Kotlin as an exception. */
private fun settle(p: Promise<JsAny?>): Promise<JsAny?> = js("p.then(() => null, (e) => e || {})")

private fun errorCode(e: JsAny): String = js("String(e.code || 'unknown')")
private fun errorEmail(e: JsAny): String = js("String(e.customData && e.customData.email || '')")
private fun errorMessage(e: JsAny): String = js("String(e.message || '')")

private fun provider() = GoogleAuthProvider().also { it.setCustomParameters(selectAccount()) }

private fun outcome(started: Promise<JsAny?>): suspend () -> GoogleResult {
    val settled = settle(started)
    return {
        val error = settled.await<JsAny?>()
        if (error == null) {
            GoogleResult.Ok
        } else {
            val code = errorCode(error)
            when (code.removePrefix("auth/")) {
                "credential-already-in-use", "email-already-in-use" -> {
                    // The popup's credential is still good, so switching needn't open it again.
                    val credential = GoogleAuthProvider.credentialFromError(error)
                    val switchNow = credential?.let { c -> suspend { outcome(signInWithCredential(getAuth(), c))() } }
                    GoogleResult.InUse(errorEmail(error).ifEmpty { null }, switchNow)
                }
                "popup-closed-by-user", "cancelled-popup-request" -> GoogleResult.Cancelled
                else -> GoogleResult.Failed(googleErrorText(code, errorMessage(error)))
            }
        }
    }
}

actual fun platformGoogleLink(): suspend () -> GoogleResult {
    val user = getAuth().currentUser ?: return { GoogleResult.Failed("還沒登入，請重新整理後再試") }
    return outcome(linkWithPopup(user, provider()))
}

actual fun platformGoogleSignIn(): suspend () -> GoogleResult = outcome(signInWithPopup(getAuth(), provider()))
