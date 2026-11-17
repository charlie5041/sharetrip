package tw.tripshare.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

/** How a Google step ended, worded later for the person. */
sealed interface GoogleResult {
    data object Ok : GoogleResult
    data object Cancelled : GoogleResult
    /** That Google account already backs another 同遊 identity. */
    data class InUse(val email: String?) : GoogleResult
    data class Failed(val message: String) : GoogleResult
}

/**
 * Keeps a device's anonymous identity recoverable by tying it to a Google
 * account. Linking keeps the anonymous uid, so every trip stays put; on a
 * new device, signing in with the same Google account brings that uid back.
 *
 * [startLink] and [startSignIn] must be called straight from a tap: the web
 * opens Google's popup there, and a browser blocks a popup opened later.
 * The returned function waits for the outcome.
 */
object Google {
    /** The linked Google address, or null while this identity is anonymous only. */
    fun linkedEmail(): String? {
        val user = Firebase.auth.currentUser ?: return null
        val google = user.providerData.firstOrNull { it.providerId == "google.com" } ?: return null
        return google.email ?: user.email ?: ""
    }

    fun startLink(): suspend () -> GoogleResult = platformGoogleLink()

    /** Signs in as the identity tied to a Google account; the app must then restart its session. */
    fun startSignIn(): suspend () -> GoogleResult = platformGoogleSignIn()
}

/** The project's Google web client id; Android asks for an id token addressed to it. */
const val GOOGLE_WEB_CLIENT_ID = "307482465264-nibtr9etkrgl0dbhgjks34r9jbim20b4.apps.googleusercontent.com"

expect fun platformGoogleLink(): suspend () -> GoogleResult
expect fun platformGoogleSignIn(): suspend () -> GoogleResult

/** Words for a failed step, keyed by Firebase's error code. */
fun googleErrorText(code: String, message: String?): String = when (code.removePrefix("auth/")) {
    "popup-closed-by-user", "cancelled-popup-request", "user-cancelled" -> "已取消"
    "popup-blocked" -> "瀏覽器擋下了登入視窗，請允許彈出視窗後再試"
    "operation-not-allowed" -> "Google 登入還沒在 Firebase 開啟"
    "unauthorized-domain" -> "這個網址還沒被允許用 Google 登入"
    "network-request-failed" -> "網路不通，請稍後再試"
    else -> "Google 登入失敗（$code${if (message.isNullOrBlank()) "" else "：$message"}）"
}
