package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.ConfirmDialog
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Screen
import tw.tripshare.ui.SectionTitle
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt

fun money(c: Currency, minor: Long) = "${c.format(minor)} ${c.code}"

/** "12/20 18:30" in the phone's time zone. */
fun shortWhen(millis: Long): String {
    val t = localDateTime(millis)
    return "${t.month.ordinal + 1}/${t.day} ${hm(t.hour, t.minute)}"
}

fun categoryColor(c: ExpenseCategory): Color = when (c) {
    ExpenseCategory.food, ExpenseCategory.shopping -> AppColors.coral
    ExpenseCategory.transport -> AppColors.mizu
    ExpenseCategory.lodging -> AppColors.wakatake
    ExpenseCategory.ticket -> AppColors.yamabuki
    ExpenseCategory.other -> AppColors.white
}

/** A trip's total in the base currency, or null when some stored expense can't be converted. */
private fun totalOrNull(data: TripData): Long? =
    runCatching { data.expenses.sumOf { baseTotal(it, data.base) } }.getOrNull()

@Composable
fun ExpensesTab(data: TripData) {
    val nav = LocalNav.current
    InkButton("＋ 記一筆", { nav.push(Screen.ExpenseForm(data.trip.id, null)) }, Modifier.fillMaxWidth())
    if (data.expenses.isEmpty()) {
        CenterMessage("還沒有支出，按上面記第一筆")
        return
    }
    InkCard(Modifier.fillMaxWidth(), fill = AppColors.yamabuki) {
        val total = totalOrNull(data)
        Txt(if (total == null) "總花費算不出來（有一筆資料有誤）" else "總花費 ${money(data.base, total)}", size = 20, bold = true)
    }
    data.expenses.forEach { e -> ExpenseRow(data, e) }
}

@Composable
private fun ExpenseRow(data: TripData, e: Expense) {
    val nav = LocalNav.current
    val cur = runCatching { Currency.of(e.currency) }.getOrNull()
    InkCard(Modifier.fillMaxWidth(), onClick = { nav.push(Screen.ExpenseForm(data.trip.id, e.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).border(2.dp, AppColors.ink, CircleShape).background(categoryColor(e.category), CircleShape),
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
                    Txt(if (cur == null) "${e.amountMinor} ${e.currency}" else money(cur, e.amountMinor), bold = true)
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
                    color = if (v < 0) AppColors.error else AppColors.ink,
                    align = TextAlign.End,
                )
            }
        }
    }

    SectionTitle("怎麼轉帳")
    if (transfers.isEmpty()) {
        InkCard(Modifier.fillMaxWidth()) { Txt("大家都結清了 🎉") }
    }
    transfers.forEach { t ->
        InkCard(Modifier.fillMaxWidth(), fill = AppColors.mizu) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Txt("${data.nameOf(t.fromId)} → ${data.nameOf(t.toId)}", bold = true)
                    Txt(money(base, t.amountMinor), size = 18)
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
