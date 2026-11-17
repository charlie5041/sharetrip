package tw.tripshare.data

import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tw.tripshare.platformName

/**
 * Sends crashes and what people type into 回報問題 to Firestore's
 * `errorReports`, where only the Firebase Console can read them. Same
 * fields and limits as the Flutter app, which the security rules enforce.
 */
object Reporter {
    /** Shown in reports so they say which build; "k" marks the Kotlin app. */
    const val VERSION = "0.2.0-k"

    private const val MAX_ERRORS = 20
    private const val MAX_MESSAGE = 2000
    private const val MAX_STACK = 4000

    private var target: Pair<FirebaseFirestore, String>? = null
    private var scope: CoroutineScope? = null
    private val early = mutableListOf<Pair<String, String?>>()
    private val seen = mutableSetOf<String>()
    private var sent = 0

    /** Called once signed in; errors from before that go out now. */
    fun attach(db: FirebaseFirestore, uid: String, background: CoroutineScope) {
        target = db to uid
        scope = background
        val pending = early.toList()
        early.clear()
        pending.forEach { (m, s) -> recordError(m, s) }
    }

    /** Records an uncaught error once per message, at most [MAX_ERRORS] a session. */
    fun recordError(message: String, stack: String?) {
        if (target == null) {
            if (early.size < MAX_ERRORS) early += message to stack
            return
        }
        val m = cut(message, MAX_MESSAGE)
        if (!seen.add(m) || sent >= MAX_ERRORS) return
        sent++
        write("error", m, stack?.let { cut(it, MAX_STACK) })
    }

    /** False when not signed in yet, so the caller can say to try later. */
    fun sendFeedback(text: String): Boolean {
        if (target == null) return false
        write("feedback", cut(text.trim(), MAX_MESSAGE), null)
        return true
    }

    private fun write(kind: String, message: String, stack: String?) {
        val (db, uid) = target ?: return
        val data = buildMap<String, Any?> {
            put("uid", uid)
            put("kind", kind)
            put("message", message)
            if (stack != null) put("stack", stack)
            put("version", "$VERSION ${platformName()}".take(100))
            put("createdAt", FieldValue.serverTimestamp)
        }
        // Offline the write finishes once the phone reconnects; failing to
        // report must never become an error of its own.
        scope?.launch { runCatching { db.collection("errorReports").add(data) } }
    }

    private fun cut(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1) + "…"
}
