package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import tw.tripshare.ui.onColor
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import tw.tripshare.core.ExpenseDay
import tw.tripshare.core.groupByDay
import tw.tripshare.core.shortDate
import tw.tripshare.core.tripDayNumber
import tw.tripshare.core.ymd
import tw.tripshare.today
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.tripshare.core.Currency
import tw.tripshare.core.Expense
import tw.tripshare.core.ExpenseCategory
import tw.tripshare.core.balances
import tw.tripshare.core.baseTotal
import tw.tripshare.core.hm
import tw.tripshare.core.Transfer
import tw.tripshare.core.suggestTransfers
import tw.tripshare.localDateTime
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.AppShape
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.HeroCard
import tw.tripshare.ui.Looks
import tw.tripshare.ui.ConfirmDialog
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Screen
import tw.tripshare.ui.SectionTitle
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt
import tw.tripshare.ui.deco

fun money(c: Currency, minor: Long) = "${grouped(c.format(minor))} ${c.code}"

/** "1234567.5" → "1,234,567.5", so big amounts read at a glance. */
fun grouped(n: String): String {
    val neg = n.startsWith("-")
    val digits = n.removePrefix("-")
    val whole = digits.substringBefore('.')
    val rest = digits.substring(whole.length)
    return (if (neg) "-" else "") + whole.reversed().chunked(3).joinToString(",").reversed() + rest
}

/** "12/20 18:30" in the phone's time zone. */
fun shortWhen(millis: Long): String {
    val t = localDateTime(millis)
    return "${t.month.ordinal + 1}/${t.day} ${hm(t.hour, t.minute)}"
}

/** A soft tint per category, behind its first letter. */
fun categoryColor(c: ExpenseCategory): Color {
    val scrap = Looks.scrapbook
    return Color(
        when (c) {
            ExpenseCategory.food -> if (scrap) 0xFFFFE3D8 else 0xFFFFF1E6
            ExpenseCategory.transport -> if (scrap) 0xFFDCEBFF else 0xFFEAF2FF
            ExpenseCategory.lodging -> if (scrap) 0xFFDDF0DE else 0xFFE6F7EF
            ExpenseCategory.ticket -> if (scrap) 0xFFFFF3C4 else 0xFFF3E8FF
            ExpenseCategory.shopping -> if (scrap) 0xFFF5E1F0 else 0xFFFFE4EC
            ExpenseCategory.other -> if (scrap) 0xFFEFE8DD else 0xFFF1F3F5
        },
    )
}

/** A trip's total in the base currency, or null when some stored expense can't be converted. */
private fun totalOrNull(data: TripData): Long? =
    runCatching { data.expenses.sumOf { baseTotal(it, data.base) } }.getOrNull()

@Composable
fun ExpensesTab(data: TripData) {
    if (data.expenses.isEmpty()) {
        CenterMessage("還沒有支出，按下面的 ＋ 記第一筆")
        return
    }
    HeroCard(Modifier.fillMaxWidth()) {
        val total = totalOrNull(data)
        Txt(if (Looks.scrapbook) "這趟一共花了" else "總花費", size = 14)
        if (total == null) {
            Txt("算不出來（有一筆資料有誤）", size = 20, bold = true)
        } else {
            Txt(money(data.base, total), size = 34, bold = true, maxLines = 1)
            val people = data.members.size
            if (people > 1) Txt("$people 人 · 每人約 ${money(data.base, total / people)}", size = 14)
        }
    }
    ExpenseDays(data)
}

/**
 * The expenses by day, each day folding open and shut. Today (or the newest
 * day, when nothing was spent today) starts open; what's open is remembered
 * while editing an expense and coming back.
 */
@Composable
private fun ExpenseDays(data: TripData) {
    val nav = LocalNav.current
    val days = groupByDay(data.expenses) { localDateTime(it).date }
    val todayYmd = ymd(today())
    // Days as "yyyy-MM-dd" joined by commas; null until the person folds anything.
    var openText by rememberSaveable(data.trip.id) { mutableStateOf<String?>(null) }
    val open: Set<String> = openText?.split(',')?.filter { it.isNotEmpty() }?.toSet()
        ?: setOf(days.firstOrNull { ymd(it.date) == todayYmd }?.let { ymd(it.date) } ?: ymd(days.first().date))
    fun setOpen(s: Set<String>) { openText = s.joinToString(",") }

    val allOpen = days.all { ymd(it.date) in open }
    SectionTitle("${days.size} 天 · ${data.expenses.size} 筆") {
        TextAction(if (allOpen) "全部收起" else "全部展開", {
            setOpen(if (allOpen) emptySet() else days.map { ymd(it.date) }.toSet())
        }, color = AppColors.coral)
    }
    days.forEach { d ->
        val key = ymd(d.date)
        val isOpen = key in open
        DayHeader(
            data, d, isOpen,
            onToggle = { setOpen(if (isOpen) open - key else open + key) },
            onAdd = { nav.push(Screen.ExpenseForm(data.trip.id, null, key)) },
        )
        if (isOpen) d.expenses.forEach { e -> ExpenseRow(data, e) }
    }
}

/** A day's date, its trip day, and what it cost; tapping folds the day. */
@Composable
private fun DayHeader(data: TripData, day: ExpenseDay, open: Boolean, onToggle: () -> Unit, onAdd: () -> Unit) {
    val key = ymd(day.date)
    val total = runCatching { day.expenses.sumOf { baseTotal(it, data.base) } }.getOrNull()
    val n = tripDayNumber(day.date, data.trip.startDate, data.trip.endDate)
    val pending = day.expenses.count { it.amountMinor == 0L }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(AppShape.small)
            .background(if (open) AppColors.yamabuki else AppColors.card, AppShape.small)
            .clickable(role = Role.Button, onClickLabel = if (open) "收起" else "展開", onClick = onToggle)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(if (open) "▾" else "▸", size = 16, bold = true)
        Column(Modifier.weight(1f)) {
            Txt(
                shortDate(key) + (n?.let { " · 第 $it 天" } ?: "") + if (key == ymd(today())) " · 今天" else "",
                bold = true, maxLines = 1,
            )
            Txt(
                "${day.expenses.size} 筆" + (if (pending > 0) " · $pending 筆待補" else "") +
                    (total?.let { " · ${money(data.base, it)}" } ?: ""),
                size = 13, color = if (pending > 0) AppColors.error else AppColors.faint, maxLines = 1,
            )
        }
        TextAction("＋ 這天", onAdd, color = AppColors.coral)
    }
}

@Composable
private fun ExpenseRow(data: TripData, e: Expense) {
    val nav = LocalNav.current
    val session = LocalSession.current
    val toasts = LocalToasts.current
    val cur = runCatching { Currency.of(e.currency) }.getOrNull()
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        ConfirmDialog("刪除「${e.title}」？", "刪掉後大家的結算會跟著改。", "刪除", onConfirm = {
            confirming = false
            session.background.launch {
                runCatching { session.repo.deleteExpense(data.trip.id, e.id) }.onFailure { toasts.show("沒有刪除成功：${it.message}") }
            }
        }, onDismiss = { confirming = false })
    }
    SwipeToDelete(onDelete = { confirming = true }) {
        InkCard(Modifier.fillMaxWidth(), onClick = { nav.push(Screen.ExpenseForm(data.trip.id, e.id)) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).border(AppShape.line, AppColors.line, CircleShape).background(categoryColor(e.category), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Txt(e.category.label.take(1)) }
                Column(Modifier.weight(1f)) {
                    Txt(e.title, bold = true, maxLines = 2)
                    Txt("${data.nameOf(e.payerId)} 付 · ${shortWhen(e.occurredAt)}", size = 13, color = AppColors.faint)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (e.amountMinor == 0L) {
                        // Imported from a sheet row without an amount; easy to forget.
                        Txt("待補金額", bold = true, color = AppColors.error)
                    } else {
                        Txt(if (cur == null) "${e.amountMinor} ${e.currency}" else money(cur, e.amountMinor), size = 18, bold = true)
                    }
                    if (e.currency != data.base.code) {
                        runCatching { baseTotal(e, data.base) }.getOrNull()?.let {
                            Txt("≈ ${money(data.base, it)}", size = 13, color = AppColors.faint)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Drag [content] to the right to delete it: a red 刪除 shows underneath, and
 * letting go past a third of the width (or with a quick flick) calls
 * [onDelete], which asks before anything is removed. The row slides back
 * either way. Deleting from the edit screen still works for anyone who
 * can't swipe.
 */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val drag = rememberDraggableState { d ->
        scope.launch { offset.snapTo((offset.value + d).coerceIn(0f, width.toFloat())) }
    }
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        if (offset.value > 0f) {
            Box(
                Modifier.matchParentSize().clip(AppShape.radius).background(AppColors.error, AppShape.radius).padding(start = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Txt("刪除", bold = true, color = onColor(AppColors.error)) }
        }
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }.draggable(
                drag, Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val far = offset.value > width / 3f || (velocity > 1500f && offset.value > 0f)
                    offset.animateTo(0f)
                    if (far) onDelete()
                },
            ),
        ) { content() }
    }
}

@Composable
fun SettleTab(data: TripData) {
    val session = LocalSession.current
    val toasts = LocalToasts.current
    val base = data.base
    val net = runCatching { balances(data.members, data.expenses, data.settlements, base) }
    val n = net.getOrElse {
        CenterMessage("有一筆帳的資料有誤，算不出結算：${it.message}")
        return
    }
    val transfers = suggestTransfers(n)
    // Expenses imported without an amount count as 0 until someone fills them in.
    val pending = data.expenses.filter { it.amountMinor == 0L }
    var confirming by remember { mutableStateOf<Transfer?>(null) }

    fun write(failure: String, block: suspend () -> Unit) {
        session.background.launch { runCatching { block() }.onFailure { toasts.show(failure) } }
    }

    if (pending.isNotEmpty()) {
        InkCard(Modifier.fillMaxWidth(), fill = AppColors.yamabuki) {
            Txt("還有 ${pending.size} 筆花費沒填金額", bold = true)
            Txt("下面的結算先當它們是 0 元：" + pending.take(3).joinToString("、") { it.title } + if (pending.size > 3) " 等" else "", size = 14)
        }
    }

    SectionTitle("每個人的狀況")
    data.members.forEach { m ->
        val v = n[m.id] ?: 0
        InkCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(m.name, Modifier.weight(1f), bold = true)
                Txt(
                    when {
                        v == 0L -> "剛好"
                        v > 0 -> "應收 ${money(base, v)}"
                        else -> "應付 ${money(base, -v)}"
                    },
                    color = when {
                        v < 0 -> AppColors.error
                        v > 0 -> AppColors.good
                        else -> AppColors.faint
                    },
                    size = 18,
                    bold = true,
                    align = TextAlign.End,
                )
            }
        }
    }

    SectionTitle("怎麼轉帳")
    if (transfers.isEmpty()) {
        InkCard(Modifier.fillMaxWidth()) { Txt("大家都結清了" + deco("🎉", before = false)) }
    }
    transfers.forEach { t ->
        InkCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Txt("${data.nameOf(t.fromId)} → ${data.nameOf(t.toId)}", bold = true)
                    Txt(money(base, t.amountMinor), size = 22, bold = true)
                }
                InkButton("已轉帳", {
                    if (pending.isNotEmpty()) confirming = t
                    else write("沒有記成功，請再試一次") { session.repo.addSettlement(data.trip.id, t.fromId, t.toId, t.amountMinor) }
                })
            }
        }
    }

    confirming?.let { t ->
        ConfirmDialog(
            "還有花費沒填金額",
            "${pending.size} 筆花費還是 0 元（${pending.take(3).joinToString("、") { it.title }}${if (pending.size > 3) " 等" else ""}），這筆轉帳金額可能少算。確定要記成已轉帳？",
            "確定",
            onConfirm = {
                confirming = null
                write("沒有記成功，請再試一次") { session.repo.addSettlement(data.trip.id, t.fromId, t.toId, t.amountMinor) }
            },
            onDismiss = { confirming = null },
        )
    }

    if (data.settlements.isNotEmpty()) {
        SectionTitle("已轉帳紀錄")
        data.settlements.forEach { s ->
            InkCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt("${data.nameOf(s.fromId)} → ${data.nameOf(s.toId)}", bold = true)
                        Txt("${money(base, s.amountMinor)} · ${shortWhen(s.at)}", size = 13, color = AppColors.faint)
                    }
                    TextAction("取消這筆", {
                        write("沒有取消成功，請再試一次") { session.repo.deleteSettlement(data.trip.id, s.id) }
                    })
                }
            }
        }
    }
}
