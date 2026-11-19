package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import tw.tripshare.core.ChecklistEntry
import tw.tripshare.core.Currency
import tw.tripshare.core.Expense
import tw.tripshare.core.Flight
import tw.tripshare.core.ItineraryItem
import tw.tripshare.core.Lodging
import tw.tripshare.core.Member
import tw.tripshare.core.Settlement
import tw.tripshare.core.Trip
import tw.tripshare.nowMillis
import tw.tripshare.ui.AddButton
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.AppShape
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.Looks
import tw.tripshare.ui.Page
import tw.tripshare.ui.Screen
import tw.tripshare.ui.Txt

/** Everything a trip's screens read, gathered from the live streams. */
data class TripData(
    val trip: Trip,
    val members: List<Member>,
    val expenses: List<Expense>,
    val settlements: List<Settlement>,
    val items: List<ItineraryItem>,
    val flights: List<Flight>,
    val lodgings: List<Lodging>,
    val checklist: List<ChecklistEntry>,
    val myUid: String,
) {
    val me: Member? get() = members.firstOrNull { it.uid == myUid }
    val isOwner: Boolean get() = trip.ownerUid == myUid
    val base: Currency get() = Currency.of(trip.baseCurrency)
    fun nameOf(memberId: String): String = members.firstOrNull { it.id == memberId }?.name ?: "（已移除）"
}

sealed interface Load<out T> {
    data object Waiting : Load<Nothing>
    data object Gone : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/** Retries a refused stream after 0.5, 1, 2 and 4 seconds, then lets the error through. */
private fun <T> Flow<T>.retryBriefly(): Flow<T> = retryWhen { _, attempt ->
    if (attempt < 4) { delay(500L shl attempt.toInt()); true } else false
}

/** A trip and its lists, live. Gone once the trip is dissolved or this device is no longer a member. */
@Composable
fun rememberTripData(tripId: String): Load<TripData> {
    val repo = LocalSession.current.repo
    // A read error means the rules refused: this device is no longer a member.
    // Or the trip was just created and the server hasn't seen it yet, which
    // is why each stream retries for a few seconds before giving up.
    val trip by remember(tripId) {
        repo.watchTrip(tripId).retryBriefly().map { t -> if (t == null) Load.Gone else Load.Ready(t) }.catch { emit(Load.Gone) }
    }.collectAsState(Load.Waiting)
    val members by remember(tripId) { repo.watchMembers(tripId).retryBriefly().catch { } }.collectAsState(null)
    val expenses by remember(tripId) { repo.watchExpenses(tripId).retryBriefly().catch { } }.collectAsState(null)
    val settlements by remember(tripId) { repo.watchSettlements(tripId).retryBriefly().catch { } }.collectAsState(null)
    val items by remember(tripId) { repo.watchItems(tripId).retryBriefly().catch { } }.collectAsState(null)
    val flights by remember(tripId) { repo.watchFlights(tripId).retryBriefly().catch { } }.collectAsState(null)
    val lodgings by remember(tripId) { repo.watchLodgings(tripId).retryBriefly().catch { } }.collectAsState(null)
    val checklist by remember(tripId) { repo.watchChecklist(tripId).retryBriefly().catch { } }.collectAsState(null)
    val t = trip
    if (t is Load.Gone) return Load.Gone
    if (t !is Load.Ready) return Load.Waiting
    return Load.Ready(
        TripData(
            t.value,
            members ?: return Load.Waiting,
            expenses ?: return Load.Waiting,
            settlements ?: return Load.Waiting,
            items ?: return Load.Waiting,
            flights ?: return Load.Waiting,
            lodgings ?: return Load.Waiting,
            checklist ?: return Load.Waiting,
            repo.uid,
        ),
    )
}

/** Shows the waiting and gone states, and [content] once the trip is loaded. */
@Composable
fun WithTrip(tripId: String, content: @Composable (TripData) -> Unit) {
    val nav = LocalNav.current
    when (val load = rememberTripData(tripId)) {
        Load.Gone -> Page("", onBack = { nav.pop() }) { CenterMessage("這趟旅程已經解散，或你已經不在成員名單裡。") }
        Load.Waiting -> Page("", onBack = { nav.pop() }) { CenterMessage("讀取中…") }
        is Load.Ready -> content(load.value)
    }
}

private const val STAMP_EVERY_MILLIS = 24L * 3600 * 1000

private val TABS = listOf("總覽", "行程", "記帳", "結算", "清單", "成員")

@Composable
fun TripScreen(tripId: String) {
    val nav = LocalNav.current
    var tab by rememberSaveable(tripId) { mutableIntStateOf(0) }
    WithTrip(tripId) { data ->
        // The owner's visit tells everyone the owner is still around (see Trip.ownerAbsent).
        val repo = LocalSession.current.repo
        val background = LocalSession.current.background
        LaunchedEffect(tripId, data.isOwner) {
            val seen = data.trip.ownerSeenAt
            if (data.isOwner && (seen == null || nowMillis() - seen > STAMP_EVERY_MILLIS)) {
                background.launch { runCatching { repo.stampOwnerSeen(tripId) } }
            }
        }
        val quickAdd = TABS[tab] in setOf("總覽", "記帳", "結算")
        Page(
            data.trip.name,
            onBack = { nav.pop() },
            bottom = { TabBar(tab) { tab = it } },
            fab = if (quickAdd) ({ AddButton({ nav.push(Screen.ExpenseForm(tripId, null)) }) }) else null,
        ) {
            when (TABS[tab]) {
                "總覽" -> OverviewTab(data)
                "行程" -> ItineraryTab(data)
                "記帳" -> ExpensesTab(data)
                "結算" -> SettleTab(data)
                "清單" -> ChecklistTab(data)
                else -> MembersTab(data)
            }
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val scrap = Looks.scrapbook
    val line = AppColors.line
    Row(
        Modifier.fillMaxWidth()
            .background(AppColors.card)
            .drawBehind {
                drawLine(
                    line, Offset(0f, 0f), Offset(size.width, 0f), if (scrap) 2.dp.toPx() else 1.dp.toPx(),
                    pathEffect = if (scrap) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null,
                )
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TABS.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(AppShape.small)
                    .background(if (on && scrap) AppColors.yamabuki else AppColors.card, AppShape.small)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Txt(
                    label, size = 14, bold = on,
                    color = when {
                        !on -> AppColors.faint
                        scrap -> AppColors.ink
                        else -> AppColors.coral
                    },
                )
            }
        }
    }
}
