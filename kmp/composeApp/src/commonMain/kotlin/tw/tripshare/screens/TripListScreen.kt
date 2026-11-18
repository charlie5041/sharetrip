package tw.tripshare.screens

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
            ActionRow("回報問題", "哪裡怪怪的，直接告訴開發者") { menu = false; feedback = true }
            Txt("版本 ${Reporter.VERSION}", size = 13, color = AppColors.faint)
        }
    }
    if (feedback) FeedbackDialog { feedback = false }
    if (account) AccountDialog { account = false }
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
