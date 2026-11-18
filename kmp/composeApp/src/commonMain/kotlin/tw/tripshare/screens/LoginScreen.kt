package tw.tripshare.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import tw.tripshare.core.Trip
import tw.tripshare.data.Google
import tw.tripshare.data.GoogleResult
import tw.tripshare.data.TripRepository
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
 * The only screen until the person is signed in with Google.
 *
 * [anonymous] is the repository of an identity from before Google was
 * required: it is linked to the Google account (same uid, so every trip
 * stays), and its trips are named if that account already backs another
 * identity. Null when nobody is signed in on this device yet.
 * [onSignedIn] starts the app over as whoever is signed in now.
 */
@Composable
fun LoginScreen(anonymous: TripRepository?, onSignedIn: () -> Unit) {
    val env = remember { installEnv() }
    val scope = rememberCoroutineScope()
    // The rules refuse anonymous accounts, so this usually comes from the
    // phone's cache, or not at all (unknown: never switch without asking).
    val tripsFlow: Flow<List<Trip>?> = remember(anonymous) {
        anonymous?.watchMyTrips()?.catch { emit(null) } ?: flowOf(emptyList())
    }
    val trips by tripsFlow.collectAsState(null)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inUse by remember { mutableStateOf<GoogleResult.InUse?>(null) }

    fun settle(r: GoogleResult) {
        when (r) {
            GoogleResult.Ok -> scope.launch {
                // The rules only let in a Google-backed token; after linking,
                // get one that says so before Firestore is used again.
                runCatching { Firebase.auth.currentUser?.getIdToken(true) }
                onSignedIn()
            }
            GoogleResult.Cancelled -> {}
            is GoogleResult.InUse -> inUse = r
            is GoogleResult.Failed -> error = r.message
        }
    }

    // Google's window must open inside the tap, so start* is called here
    // and only its outcome is awaited later.
    fun go() {
        if (busy) return
        busy = true
        error = null
        val outcome = if (anonymous == null) Google.startSignIn() else Google.startLink()
        scope.launch {
            val r = outcome()
            val now = (r as? GoogleResult.InUse)?.switchNow
            // This identity has no trips, so nothing is lost by taking the one the account backs.
            if (now != null && trips?.isEmpty() == true) settle(now()) else settle(r)
            busy = false
        }
    }

    Page("同遊") {
        val inApp = env?.inAppBrowser
        if (env != null && inApp != null) {
            // Google refuses to sign in inside LINE, Facebook and the like.
            val browser = if (env.platform == "ios") "Safari" else "Chrome"
            CenterMessage("你現在是在 $inApp 裡面開啟同遊。\nGoogle 不允許在這裡登入，請改用 $browser 打開。") {
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
        val intro = if (anonymous == null) {
            "同遊需要用 Google 帳號登入。\n換手機或資料被清掉時，用同一個帳號登入就能找回所有旅程。"
        } else {
            "從這一版開始，同遊需要綁定 Google 帳號才能使用。\n綁定後你的旅程、帳和名字都會保留。"
        }
        CenterMessage(intro) {
            InkButton(
                if (anonymous == null) "用 Google 登入" else "綁定 Google 帳號",
                ::go,
                Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            error?.let { Txt(it, color = AppColors.error) }
        }
    }

    val pending = inUse
    if (pending != null) {
        val reuse = pending.switchNow
        val names = trips.orEmpty().joinToString("、") { "「${it.name}」" }.ifEmpty { "旅程" }
        InkDialog("這個 Google 帳號已經用過", { inUse = null }, buttons = {
            TextAction("換一個帳號", { inUse = null })
            InkButton("改用那個身分", {
                inUse = null
                if (reuse == null) {
                    // Android can't reuse the account just picked, so pick it again.
                    busy = true
                    val outcome = Google.startSignIn()
                    scope.launch { settle(outcome()); busy = false }
                } else {
                    busy = true
                    scope.launch { settle(reuse()); busy = false }
                }
            })
        }) {
            Txt("${pending.email ?: "這個 Google 帳號"} 已經綁在另一個同遊身分上，兩個身分沒辦法合併。")
            Txt(
                "改用那個身分後，這台裝置上的 $names 會看不到。帳和名字都還在：" +
                    "請群主在成員頁對你的名字按「解除綁定」，再用邀請碼加入、選回原本的名字。",
                size = 15,
            )
        }
    }
}
