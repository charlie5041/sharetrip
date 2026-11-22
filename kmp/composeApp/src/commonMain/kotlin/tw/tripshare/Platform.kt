package tw.tripshare

import dev.gitlive.firebase.FirebaseOptions
import kotlinx.datetime.LocalDate

/** Firebase project tripshare-f6209, shared with the Flutter app. */
expect val firebaseOptions: FirebaseOptions

/** The context Firebase needs on Android; null on the web. */
expect fun platformContext(): Any?

expect val isWeb: Boolean

/** Milliseconds since 1970, like Dart's millisecondsSinceEpoch. */
expect fun nowMillis(): Long

/** Today's date where the phone is. */
expect fun today(): LocalDate

/** Bytes from the platform's secure random source (invite codes). */
expect fun secureRandomBytes(n: Int): ByteArray

/** Puts [text] on the clipboard. */
expect fun copyToClipboard(text: String)

/** [millis] as the phone's local date and time. */
expect fun localDateTime(millis: Long): kotlinx.datetime.LocalDateTime

/** A local date and time on the phone → milliseconds since 1970. */
expect fun localMillis(dt: kotlinx.datetime.LocalDateTime): Long

/** The body of a GET to [url], or null on any failure. No timeout; callers add one. */
expect suspend fun httpGetText(url: String): String?

/** A GET's status and body, error responses included; null when the network failed. */
expect suspend fun httpGetResponse(url: String): Pair<Int, String>?

/** Opens [url] in the browser or the app that handles it (Google Maps for map links). */
expect fun openUrl(url: String)

/** "web-iOS", "android 14" and the like, so a report says which phone. */
expect fun platformName(): String

/** Calls [onError] with (message, stack) for errors nothing else caught. */
expect fun installCrashHook(onError: (String, String?) -> Unit)

/** Where the web app runs, for the "add to home screen" guide. */
data class InstallEnv(
    /** Already opened from the home screen. */
    val standalone: Boolean,
    /** "ios", "android" or "other". */
    val platform: String,
    /** The app whose built-in browser this is (LINE, Facebook, ...), which can't add to the home screen. */
    val inAppBrowser: String?,
    /** Android Chrome offered its own install dialog. */
    val canPrompt: Boolean,
    /** This page's address, for copying to a phone. */
    val link: String,
)

/** Null in the Android app, which never shows the guide. */
expect fun installEnv(): InstallEnv?

/** Shows Android Chrome's install dialog; true when the person installed. */
expect suspend fun promptInstall(): Boolean

/** Reopens this page in the phone's browser, from LINE's built-in one. */
expect fun openInExternalBrowser()

/** A per-device yes/no that survives restarts (localStorage on the web). */
expect fun loadFlag(key: String): Boolean
expect fun saveFlag(key: String, value: Boolean)

/** A per-device short text that survives restarts; null when unset. Saving null clears it. */
expect fun loadText(key: String): String?
expect fun saveText(key: String, value: String?)

/** The phone asks apps to keep still (accessibility). */
expect fun prefersReducedMotion(): Boolean
