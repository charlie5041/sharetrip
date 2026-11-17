package tw.tripshare

import dev.gitlive.firebase.FirebaseOptions

actual fun platformContext(): Any? = null

actual val platformName: String = "網頁（Wasm）"

actual val firebaseOptions = FirebaseOptions(
    applicationId = "1:307482465264:web:4ff23232bc0db95e3d0335",
    apiKey = "AIzaSyCUpTpd83q0uwAXZSd62i7-u5eA13Uuz1E",
    projectId = "tripshare-f6209",
    authDomain = "tripshare-f6209.firebaseapp.com",
    storageBucket = "tripshare-f6209.firebasestorage.app",
    gcmSenderId = "307482465264",
)
