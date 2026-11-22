package tw.tripshare.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch
import tw.tripshare.data.Google
import tw.tripshare.data.GoogleResult
import tw.tripshare.installEnv
import tw.tripshare.openInExternalBrowser
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkDialog
import tw.tripshare.ui.Page
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt

/**
 * The only screen until the person is signed in: with Google, or as a
 * guest (Firebase anonymous sign-in). A guest can link Google later from
 * 「⋯ → 備份我的身分」, which keeps the uid and so every trip.
 * [onSignedIn] starts the app over as whoever is signed in now.
 */
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val env = remember { installEnv() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var guestAsk by remember { mutableStateOf(false) }

    // Google's window must open inside the tap, so startSignIn is called
    // here and only its outcome is awaited later.
    fun google() {
        if (busy) return
        busy = true
        error = null
        val outcome = Google.startSignIn()
        scope.launch {
            when (val r = outcome()) {
                GoogleResult.Ok -> onSignedIn()
                GoogleResult.Cancelled -> {}
                // Only linking collides with another identity, not signing in.
                is GoogleResult.InUse -> error = "Google 登入失敗，請再試一次"
                is GoogleResult.Failed -> error = r.message
            }
            busy = false
        }
    }

    fun guest() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { Firebase.auth.signInAnonymously() }
                .onSuccess { onSignedIn() }
                .onFailure { e ->
                    val text = e.message.orEmpty()
                    error = if ("operation-not-allowed" in text || "OPERATION_NOT_ALLOWED" in text) {
                        "訪客登入還沒在 Firebase 開啟"
                    } else {
                        "訪客登入失敗（${text.ifBlank { e::class.simpleName ?: "未知錯誤" }}）"
                    }
                }
            busy = false
        }
    }

    Page("同遊") {
        val inApp = env?.inAppBrowser
        if (env != null && inApp != null) {
            // Google refuses to sign in inside LINE, Facebook and the like,
            // and a guest made here would be stuck inside that app.
            val browser = if (env.platform == "ios") "Safari" else "Chrome"
            CenterMessage("你現在是在 $inApp 裡面開啟同遊。\n請改用 $browser 打開，旅程才會留在你的手機上。") {
                if (inApp == "LINE") {
                    InkButton("用 $browser 打開", ::openInExternalBrowser, Modifier.fillMaxWidth())
                } else {
                    Txt("點右上角的「⋯」或分享按鈕，選「用瀏覽器開啟」或「在 $browser 中開啟」。", size = 15)
                }
                CopyLink(env.link)
            }
            return@Page
        }
        InstallBanner()
        CenterMessage("用 Google 登入，換手機或資料被清掉時，用同一個帳號就能找回所有旅程。\n也可以先用訪客身分試用，之後再綁定 Google。") {
            InkButton("用 Google 登入", ::google, Modifier.fillMaxWidth(), enabled = !busy)
            TextAction("先用訪客身分試用", { if (!busy) guestAsk = true })
            error?.let { Txt(it, color = AppColors.error) }
        }
    }

    if (guestAsk) {
        InkDialog("用訪客身分試用", { guestAsk = false }, buttons = {
            TextAction("改用 Google", { guestAsk = false; google() })
            InkButton("我知道了，開始用", { guestAsk = false; guest() })
        }) {
            Txt("訪客的旅程只存在這台裝置上。換手機、刪掉 app 或清除網頁資料，就找不回來了。")
            Txt("之後隨時可以在「⋯ → 備份我的身分」綁定 Google，旅程、帳和名字都會保留。", size = 15)
        }
    }
}
