package tw.tripshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dev.gitlive.firebase.FirebaseOptions
import kotlinx.datetime.LocalDate
import java.security.SecureRandom

lateinit var appContext: Context

actual fun platformContext(): Any? = appContext

actual val isWeb: Boolean = false

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun today(): LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

private val random = SecureRandom()

actual fun secureRandomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

actual fun copyToClipboard(text: String) {
    val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("同遊", text))
}

actual val firebaseOptions = FirebaseOptions(
    applicationId = "1:307482465264:android:ec5f444450ac8de73d0335",
    apiKey = "AIzaSyD4wwwue4kk0ON7g_xae6adFck4tlzA2Vw",
    projectId = "tripshare-f6209",
    storageBucket = "tripshare-f6209.firebasestorage.app",
    gcmSenderId = "307482465264",
)
