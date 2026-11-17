@file:OptIn(ExperimentalWasmJsInterop::class)

package tw.tripshare

import dev.gitlive.firebase.FirebaseOptions
import kotlinx.datetime.LocalDate

actual fun platformContext(): Any? = null

actual val isWeb: Boolean = true

private fun jsNow(): Double = js("Date.now()")
private fun jsYear(): Int = js("new Date().getFullYear()")
private fun jsMonth(): Int = js("new Date().getMonth() + 1")
private fun jsDay(): Int = js("new Date().getDate()")
private fun jsRandomByte(): Int = js("crypto.getRandomValues(new Uint8Array(1))[0]")
private fun jsCopy(text: String): Unit = js("navigator.clipboard && navigator.clipboard.writeText(text)")

actual fun nowMillis(): Long = jsNow().toLong()

actual fun today(): LocalDate = LocalDate(jsYear(), jsMonth(), jsDay())

actual fun secureRandomBytes(n: Int): ByteArray = ByteArray(n) { jsRandomByte().toByte() }

actual fun copyToClipboard(text: String) = jsCopy(text)

actual val firebaseOptions = FirebaseOptions(
    applicationId = "1:307482465264:web:4ff23232bc0db95e3d0335",
    apiKey = "AIzaSyCUpTpd83q0uwAXZSd62i7-u5eA13Uuz1E",
    projectId = "tripshare-f6209",
    authDomain = "tripshare-f6209.firebaseapp.com",
    storageBucket = "tripshare-f6209.firebasestorage.app",
    gcmSenderId = "307482465264",
)
