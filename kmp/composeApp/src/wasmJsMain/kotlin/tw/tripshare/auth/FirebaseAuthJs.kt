@file:JsModule("firebase/auth")
@file:OptIn(ExperimentalWasmJsInterop::class)

package tw.tripshare.auth

import kotlin.js.Promise

/**
 * The Firebase JS SDK's own auth functions, for the Google popup the
 * Kotlin wrapper doesn't offer. Same npm package (and so the same signed-in
 * user) as the wrapper uses.
 */
external interface JsAuth : JsAny {
    val currentUser: JsAny?
}

external fun getAuth(): JsAuth

external class GoogleAuthProvider : JsAny {
    fun setCustomParameters(params: JsAny): JsAny

    companion object {
        /** The Google credential a failed popup step carries, or null. */
        fun credentialFromError(error: JsAny): JsAny?
    }
}

external fun linkWithPopup(user: JsAny, provider: GoogleAuthProvider): Promise<JsAny?>

external fun signInWithPopup(auth: JsAuth, provider: GoogleAuthProvider): Promise<JsAny?>

external fun signInWithCredential(auth: JsAuth, credential: JsAny): Promise<JsAny?>
