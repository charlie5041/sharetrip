package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.tripshare.InstallEnv
import tw.tripshare.copyToClipboard
import tw.tripshare.data.Google
import tw.tripshare.data.GoogleResult
import tw.tripshare.data.Reporter
import tw.tripshare.installEnv
import tw.tripshare.loadFlag
import tw.tripshare.openInExternalBrowser
import tw.tripshare.promptInstall
import tw.tripshare.saveFlag
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.AppShape
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.InkDialog
import tw.tripshare.ui.InkField
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt

/** Lets a friend tell Charlie what went wrong, from inside the app. */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val toasts = LocalToasts.current
    var text by remember { mutableStateOf("") }
    InkDialog("回報問題", onDismiss, buttons = {
        TextAction("取消", onDismiss)
        InkButton("送出", {
            if (text.isNotBlank()) toasts.show(if (Reporter.sendFeedback(text)) "謝謝！已經送出" else "現在送不出去，請稍後再試")
            onDismiss()
        }, enabled = text.isNotBlank())
    }) {
        InkField(text, { text = it }, "哪裡怪怪的？在哪個畫面、做了什麼？", maxLength = 1000, lines = 3)
    }
}

private const val DISMISSED = "installGuideDismissed"

/**
 * A card on the web app inviting the person to add 同遊 to the home
 * screen. Hidden once it's opened from there, or after they close it.
 */
@Composable
fun InstallBanner() {
    val env = remember { installEnv() } ?: return
    var dismissed by remember { mutableStateOf(loadFlag(DISMISSED)) }
    var open by remember { mutableStateOf(false) }
    if (env.standalone || dismissed) return
    val subtitle = when {
        env.inAppBrowser != null -> "先用手機的瀏覽器打開，才能加到主畫面"
        env.platform == "ios" -> "先加到主畫面，再從主畫面打開開始用"
        else -> "像 App 一樣從桌面打開，沒網路也能用"
    }
    InkCard(Modifier.fillMaxWidth(), fill = AppColors.mizu, onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("把同遊加到主畫面", bold = true)
                Txt(subtitle, size = 13)
            }
            TextAction("怎麼加", { open = true })
            TextAction("✕", { dismissed = true; saveFlag(DISMISSED, true) })
        }
    }
    if (open) InstallGuideDialog(env) { open = false }
}

/** Step-by-step instructions for this phone and browser. */
@Composable
private fun InstallGuideDialog(env: InstallEnv, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ios = env.platform == "ios"
    val browser = if (ios) "Safari" else "Chrome"
    val title = when {
        env.inAppBrowser != null -> "請先用 $browser 打開"
        ios -> "加到 iPhone 主畫面"
        env.platform == "android" -> "加到手機主畫面"
        else -> "用手機打開同遊"
    }
    InkDialog(title, onDismiss, buttons = { InkButton("好", onDismiss) }) {
        when {
            env.inAppBrowser != null -> {
                Txt("你現在是在 ${env.inAppBrowser} 裡面開啟同遊，這裡沒辦法加到主畫面。")
                if (env.inAppBrowser == "LINE") {
                    InkButton("用 $browser 打開", ::openInExternalBrowser, Modifier.fillMaxWidth())
                } else {
                    Step(1, "點右上角的「⋯」或分享按鈕。")
                    Step(2, "選「用瀏覽器開啟」或「在 $browser 中開啟」。")
                    Step(3, "打開後再點這張卡片，照著步驟加到主畫面。")
                }
                CopyLink(env.link)
            }
            ios -> {
                Step(1, "點 Safari 下方的「分享」按鈕（方框加向上的箭頭）。沒看到的話，先點右下角的「⋯」。")
                Step(2, "往下滑，點「加入主畫面」。")
                Step(3, "名稱留著「同遊」，按右上角的「加入」。")
                Step(4, "之後都從主畫面的同遊圖示打開。")
                Box(Modifier.fillMaxWidth().background(AppColors.coral.copy(alpha = 0.25f), AppShape.small).padding(12.dp)) {
                    Txt(
                        "iPhone 會把主畫面的同遊和 Safari 裡的資料分開存。請從主畫面打開後再開始用；" +
                            "如果已經在 Safari 用過，從主畫面打開後用 Google 帳號找回旅程。",
                        size = 13,
                    )
                }
            }
            env.platform == "android" -> {
                if (env.canPrompt) {
                    InkButton("安裝同遊", { scope.launch { if (promptInstall()) onDismiss() } }, Modifier.fillMaxWidth())
                    Txt("按了沒反應的話，照下面的步驟：", size = 14)
                }
                Step(1, "點 Chrome 右上角的「⋮」。")
                Step(2, "點「加到主畫面」或「安裝應用程式」。")
                Step(3, "按「安裝」，主畫面就會出現同遊的圖示。")
                Step(4, "之後都從主畫面的同遊圖示打開。")
            }
            else -> {
                Txt("同遊是給手機用的。用手機的瀏覽器打開這個網址，再照手機上的步驟加到主畫面：")
                CopyLink(env.link)
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(28.dp).border(2.dp, AppColors.ink, CircleShape).background(AppColors.yamabuki, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Txt("$n", size = 14) }
        Txt(text, Modifier.weight(1f))
    }
}

@Composable
internal fun CopyLink(link: String) {
    var copied by remember { mutableStateOf(false) }
    InkButton(if (copied) "已複製 $link" else "複製網址", { copyToClipboard(link); copied = true }, Modifier.fillMaxWidth(), fill = AppColors.paper)
}

/**
 * Shows whether this identity is backed up to Google, and lets the person
 * back it up or switch to an identity they backed up on another device.
 * [hasTrips] says whether switching away would leave trips behind; when it
 * wouldn't, a Google account that's already in use is switched to at once.
 */
@Composable
fun AccountDialog(hasTrips: Boolean, onDismiss: () -> Unit) {
    val session = LocalSession.current
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(Google.linkedEmail()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inUse by remember { mutableStateOf<GoogleResult.InUse?>(null) }

    val switched = { onDismiss(); session.restart() }

    fun settle(r: GoogleResult, onOk: () -> Unit) {
        when (r) {
            GoogleResult.Ok -> onOk()
            GoogleResult.Cancelled -> {}
            is GoogleResult.InUse -> inUse = r
            is GoogleResult.Failed -> error = r.message
        }
    }

    // Each start* call opens Google's window right away, inside the tap.
    fun go(start: () -> (suspend () -> GoogleResult), onOk: () -> Unit) {
        if (busy) return
        busy = true
        error = null
        val outcome = start()
        scope.launch {
            val r = outcome()
            val now = (r as? GoogleResult.InUse)?.switchNow
            // Nothing to lose here, so take the identity the account already backs.
            if (now != null && !hasTrips) settle(now(), switched) else settle(r, onOk)
            busy = false
        }
    }

    val switch = { go(Google::startSignIn, switched) }

    val pending = inUse
    if (pending != null) {
        val reuse = pending.switchNow
        InkDialog("這個帳號已經綁定過", { inUse = null }, buttons = {
            TextAction("取消", { inUse = null })
            InkButton("改用那個身分", {
                inUse = null
                if (reuse == null) {
                    switch()
                } else {
                    busy = true
                    scope.launch { settle(reuse(), switched); busy = false }
                }
            })
        }) {
            Txt("${pending.email ?: "這個 Google 帳號"} 已經綁在另一個同遊身分上。要改用那個身分嗎？這台裝置目前的身分（還沒綁定）和它的旅程會被放掉。")
        }
        return
    }
    InkDialog("備份我的身分", onDismiss, buttons = { TextAction("關閉", onDismiss) }) {
        if (email != null) {
            Txt("已綁定 Google：$email\n換手機或資料不見時，用這個 Google 帳號登入就能找回所有旅程。")
        } else {
            Txt("目前沒有綁定。換手機、刪掉 app 或網頁資料被清掉時，你會看不到原本的旅程。綁定 Google 後就能找回。")
            InkButton("綁定 Google 帳號", {
                go(Google::startLink) { email = Google.linkedEmail(); toasts.show("已綁定，換手機時用 Google 登入就能找回") }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            TextAction("我在別的裝置綁定過，改用那個身分", switch)
        }
        error?.let { Txt(it, color = AppColors.error) }
    }
}
