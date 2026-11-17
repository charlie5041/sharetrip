package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import tw.tripshare.core.Member
import tw.tripshare.core.Trip
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.AppShape
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.Page
import tw.tripshare.ui.Txt

/** Everything a trip's screens read, gathered from the live streams. */
data class TripData(val trip: Trip, val members: List<Member>, val myUid: String) {
    val me: Member? get() = members.firstOrNull { it.uid == myUid }
    val isOwner: Boolean get() = trip.ownerUid == myUid
    fun nameOf(memberId: String): String = members.firstOrNull { it.id == memberId }?.name ?: "（已移除）"
}

private sealed interface Load<out T> {
    data object Waiting : Load<Nothing>
    data object Gone : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

private val TABS = listOf("總覽", "行程", "記帳", "結算", "清單", "成員")

@Composable
fun TripScreen(tripId: String) {
    val session = LocalSession.current
    val nav = LocalNav.current
    val repo = session.repo
    // A read error means the rules refused: this device is no longer a member.
    val trip by remember(tripId) {
        repo.watchTrip(tripId).map { t -> if (t == null) Load.Gone else Load.Ready(t) }.catch { emit(Load.Gone) }
    }.collectAsState(Load.Waiting)
    val members by remember(tripId) { repo.watchMembers(tripId).catch { } }.collectAsState(null)
    var tab by rememberSaveable(tripId) { mutableIntStateOf(TABS.lastIndex) }

    val t = trip
    val m = members
    if (t is Load.Gone) {
        Page("", onBack = { nav.pop() }) { CenterMessage("這趟旅程已經解散，或你已經不在成員名單裡。") }
        return
    }
    if (t !is Load.Ready || m == null) {
        Page("", onBack = { nav.pop() }) { CenterMessage("讀取中…") }
        return
    }
    val data = TripData(t.value, m, repo.uid)
    Page(data.trip.name, onBack = { nav.pop() }, bottom = { TabBar(tab) { tab = it } }) {
        when (TABS[tab]) {
            "成員" -> MembersTab(data)
            else -> CenterMessage("「${TABS[tab]}」還在搬到新版，下一步就做。")
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TABS.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .border(if (on) 2.dp else 0.dp, if (on) AppColors.ink else AppColors.paper, AppShape.small)
                    .background(if (on) AppColors.yamabuki else AppColors.paper, AppShape.small)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Txt(label, size = 14, bold = on) }
        }
    }
}
