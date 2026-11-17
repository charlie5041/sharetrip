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
