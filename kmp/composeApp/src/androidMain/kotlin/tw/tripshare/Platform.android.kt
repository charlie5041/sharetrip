package tw.tripshare

import android.content.Context
import dev.gitlive.firebase.FirebaseOptions

lateinit var appContext: Context

actual fun platformContext(): Any? = appContext

actual val platformName: String = "Android"

actual val firebaseOptions = FirebaseOptions(
    applicationId = "1:307482465264:android:ec5f444450ac8de73d0335",
    apiKey = "AIzaSyD4wwwue4kk0ON7g_xae6adFck4tlzA2Vw",
    projectId = "tripshare-f6209",
    storageBucket = "tripshare-f6209.firebasestorage.app",
    gcmSenderId = "307482465264",
)
