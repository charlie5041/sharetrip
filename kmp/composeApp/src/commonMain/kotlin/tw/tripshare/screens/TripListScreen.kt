package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import tw.tripshare.ui.AppShape
import tw.tripshare.ui.lifted
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tw.tripshare.core.Trip
import tw.tripshare.core.daysUntil
import tw.tripshare.core.shortDate
import tw.tripshare.core.tripBadge
import tw.tripshare.data.Google
import tw.tripshare.data.Reporter
import tw.tripshare.today
import tw.tripshare.ui.ActionRow
import tw.tripshare.ui.InkDialog
import tw.tripshare.ui.RoundAction
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.Looks
import tw.tripshare.ui.Look
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.Page
import tw.tripshare.ui.Screen
import tw.tripshare.ui.Tag
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt

@Composable
fun TripListScreen() {
    val session = LocalSession.current
    val nav = LocalNav.current
    val trips by remember { session.repo.watchMyTrips() }.collectAsState(null)
    var menu by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(false) }
    var looks by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(false) }
    Page(
        title = "我的旅程",
        actions = { RoundAction("⋯", { menu = true }) },
        bottom = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("用邀請碼加入", { nav.push(Screen.Join) }, Modifier.weight(1f), fill = AppColors.paper)
                InkButton("＋ 新旅程", { nav.push(Screen.NewTrip) }, Modifier.weight(1f))
            }
        },
    ) {
        InstallBanner()
        val list = trips
        when {
            list == null -> CenterMessage("讀取中…")
            list.isEmpty() -> CenterMessage("還沒有旅程\n按下面的「新旅程」建立一趟，或用邀請碼加入朋友的旅程") {
                if (Google.linkedEmail() == null) TextAction("以前用過？用 Google 帳號找回我的旅程", { account = true })
            }
            else -> list.forEach { TripCard(it) { nav.push(Screen.TripView(it.id)) } }
        }
    }
    if (menu) {
        InkDialog("同遊", { menu = false }, buttons = { TextAction("關閉", { menu = false }) }) {
            ActionRow("備份我的身分", Google.linkedEmail()?.let { "已綁定 Google $it" } ?: "還沒綁定，換手機會找不回旅程") { menu = false; account = true }
            ActionRow("外觀：${Looks.current.label}", "換成${Look.entries.filter { it != Looks.current }.joinToString("、") { "「${it.label}」" }}") { menu = false; looks = true }
            ActionRow("回報問題", "哪裡怪怪的，直接告訴開發者") { menu = false; feedback = true }
            Txt("版本 ${Reporter.VERSION}", size = 13, color = AppColors.faint)
        }
    }
    if (looks) {
        InkDialog("外觀", { looks = false }, buttons = { TextAction("完成", { looks = false }) }) {
            LookPicker()
            Txt("只會改這台裝置，朋友看到的不受影響。", size = 13, color = AppColors.faint)
        }
    }
    if (feedback) FeedbackDialog { feedback = false }
    if (account) AccountDialog(hasTrips = trips?.isNotEmpty() ?: true) { account = false }
}

@Composable
private fun TripCard(trip: Trip, onClick: () -> Unit) {
    val today = today()
    val upcoming = daysUntil(trip.startDate, today) > 0
    InkCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Txt(trip.name, size = 20, bold = true)
                Txt("${shortDate(trip.startDate)} – ${shortDate(trip.endDate)}　·　${trip.baseCurrency} 結算", size = 14)
            }
            Tag(tripBadge(trip, today), if (upcoming) AppColors.yamabuki else AppColors.wakatake)
        }
    }
}

/** The 外觀 choices, drawn in the look in use: radio buttons in minimal, paper notes with a stamp in scrapbook. */
@Composable
private fun LookPicker() {
    val scrap = Looks.scrapbook
    Look.entries.forEachIndexed { i, l ->
        val on = Looks.current == l
        if (scrap) LookNote(l, on, tilt = if (i % 2 == 0) -1f else 1f) else LookRadio(l, on)
    }
}

@Composable
private fun LookRadio(look: Look, on: Boolean) {
    val accent = AppColors.coral
    val ring = if (on) accent else AppColors.faint
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(AppShape.small)
            .selectable(selected = on, role = Role.RadioButton, onClick = { Looks.set(look) })
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(24.dp).drawBehind {
                drawCircle(ring, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                if (on) drawCircle(accent, radius = 6.dp.toPx())
            },
        )
        Column(Modifier.weight(1f)) {
            Txt(look.label, size = 17, bold = on)
            Txt(look.hint, size = 14, color = AppColors.faint)
        }
    }
}

@Composable
private fun LookNote(look: Look, on: Boolean, tilt: Float) {
    val stamp = AppColors.coral
    val sticker = if (look == Look.scrapbook) "📒" else "🤍"
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().rotate(tilt).lifted(AppShape.radius).clip(AppShape.radius)
                .background(AppColors.card, AppShape.radius)
                .then(if (on) Modifier.border(2.dp, stamp, AppShape.radius) else Modifier)
                .selectable(selected = on, role = Role.RadioButton, onClick = { Looks.set(look) })
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(42.dp).background(AppColors.yamabuki.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Txt(sticker, size = 20) }
            Column(Modifier.weight(1f)) {
                Txt(look.label, size = 18, bold = true)
                Txt(look.hint, size = 14, color = AppColors.faint)
            }
            if (on) {
                Box(
                    Modifier.size(54.dp).rotate(-12f).drawBehind {
                        drawCircle(
                            stamp, radius = size.minDimension / 2 - 1.dp.toPx(),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))),
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) { Txt("使用中", size = 12, bold = true, color = stamp) }
            }
        }
    }
}
