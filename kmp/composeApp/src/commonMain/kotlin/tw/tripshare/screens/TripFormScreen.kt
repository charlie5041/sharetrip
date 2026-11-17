package tw.tripshare.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import tw.tripshare.core.Currency
import tw.tripshare.core.shortDate
import tw.tripshare.core.ymd
import tw.tripshare.today
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.DateRangeDialog
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkDialog
import tw.tripshare.ui.InkField
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Page
import tw.tripshare.ui.RoundAction
import tw.tripshare.ui.Screen
import tw.tripshare.ui.SectionTitle
import tw.tripshare.ui.ActionRow
import tw.tripshare.ui.Tag
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripFormScreen() {
    val session = LocalSession.current
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    var name by remember { mutableStateOf("") }
    var me by remember { mutableStateOf("") }
    var friend by remember { mutableStateOf("") }
    val friends = remember { mutableStateListOf<String>() }
    var currency by remember { mutableStateOf("TWD") }
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var pickingDates by remember { mutableStateOf(false) }
    var pickingCurrency by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // The name used last time, so a second trip needs no typing.
    LaunchedEffect(Unit) {
        runCatching { session.repo.loadNickname() }.getOrNull()?.let { if (me.isEmpty()) me = it }
    }

    fun addFriend() {
        val n = friend.trim()
        if (n.isEmpty()) return
        friends += n
        friend = ""
    }

    val ready = name.isNotBlank() && start != null && me.isNotBlank()

    fun save() {
        if (!ready || saving) return
        saving = true
        val repo = session.repo
        val id = repo.newTripId()
        val others = friends.toList() + listOfNotNull(friend.trim().takeIf { it.isNotEmpty() })
        // Not awaited: offline, the write lands in the local cache at once
        // and reaches the server later.
        session.background.launch {
            runCatching { repo.setNickname(me.trim()) }
            runCatching {
                repo.createTrip(id, name, currency, ymd(start!!), ymd(end ?: start!!), me.trim(), others)
            }.onFailure { toasts.show("沒有存成功：${it.message}") }
        }
        nav.replace(Screen.TripView(id))
    }

    Page("新旅程", onBack = { nav.pop() }) {
        InkField(name, { name = it }, "旅程名稱，例如「東京五日」", maxLength = 30)
        InkButton(
            if (start == null) "選擇日期" else "${shortDate(ymd(start!!))} – ${shortDate(ymd(end ?: start!!))}",
            { pickingDates = true },
            Modifier.fillMaxWidth(),
            fill = AppColors.paper,
        )
        Txt("結算幣別（最後大家用什麼錢互轉）", size = 14, color = AppColors.faint)
        InkButton(Currency.of(currency).let { "${it.name} ${it.code}" }, { pickingCurrency = true }, Modifier.fillMaxWidth(), fill = AppColors.paper)

        SectionTitle("成員名單")
        Txt("朋友用邀請碼加入時，要從這份名單選自己的名字。之後只有你能增減成員。", size = 14)
        InkField(me, { me = it }, "你的名字", maxLength = 20)
        if (friends.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                friends.forEachIndexed { i, n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag(n, AppColors.mizu)
                        TextAction("✕", { friends.removeAt(i) })
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InkField(friend, { friend = it }, "朋友的名字", Modifier.weight(1f), maxLength = 20, onDone = ::addFriend)
            RoundAction("＋", ::addFriend, fill = AppColors.mizu)
        }
        InkButton("建立", ::save, Modifier.fillMaxWidth(), enabled = ready && !saving)
    }

    if (pickingDates) {
        DateRangeDialog(start, end, today(), onDone = { s, e -> start = s; end = e; pickingDates = false }, onDismiss = { pickingDates = false })
    }
    if (pickingCurrency) {
        InkDialog("結算幣別", { pickingCurrency = false }, buttons = { TextAction("取消", { pickingCurrency = false }) }) {
            Currency.all.forEach { c ->
                ActionRow("${c.name} ${c.code}") { currency = c.code; pickingCurrency = false }
            }
        }
    }
}
