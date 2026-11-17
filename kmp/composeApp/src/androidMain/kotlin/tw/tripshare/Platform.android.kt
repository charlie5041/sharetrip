package tw.tripshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dev.gitlive.firebase.FirebaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.security.SecureRandom

lateinit var appContext: Context

actual fun platformContext(): Any? = appContext

actual val isWeb: Boolean = false

actual fun nowMillis(): Long = System.currentTimeMillis()

// java.util.Calendar rather than java.time, which needs Android 8 and minSdk is 24.
actual fun today(): LocalDate = localDateTime(nowMillis()).date

actual fun localDateTime(millis: Long): LocalDateTime {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return LocalDateTime(
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
    )
}

actual fun localMillis(dt: LocalDateTime): Long = Calendar.getInstance().apply {
    clear()
    set(dt.year, dt.month.ordinal, dt.day, dt.hour, dt.minute)
}.timeInMillis

actual suspend fun httpGetText(url: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

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

actual fun openUrl(url: String) {
    runCatching {
        appContext.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

actual fun platformName(): String = "android ${android.os.Build.VERSION.RELEASE}"

actual fun installCrashHook(onError: (String, String?) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        // The app dies right after; Firestore keeps the write queued on disk
        // only if it got that far, so this is best effort.
        runCatching { onError(e.toString(), e.stackTraceToString()) }
        previous?.uncaughtException(t, e)
    }
}

actual fun installEnv(): InstallEnv? = null

actual suspend fun promptInstall(): Boolean = false

actual fun openInExternalBrowser() {}

private val prefs get() = appContext.getSharedPreferences("tripshare", Context.MODE_PRIVATE)

actual fun loadFlag(key: String): Boolean = prefs.getBoolean(key, false)

actual fun saveFlag(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
