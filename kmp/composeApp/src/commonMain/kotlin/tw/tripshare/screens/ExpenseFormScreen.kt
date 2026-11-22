package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import tw.tripshare.core.Calc
import tw.tripshare.core.Currency
import tw.tripshare.core.ExpenseCategory
import tw.tripshare.core.ExpenseDraft
import tw.tripshare.core.Rate
import tw.tripshare.core.SplitError
import tw.tripshare.core.SplitMode
import tw.tripshare.core.convert
import tw.tripshare.core.hm
import tw.tripshare.core.parseHm
import tw.tripshare.core.parseYmd
import tw.tripshare.core.tripDayNumber
import tw.tripshare.core.shortDate
import tw.tripshare.core.ymd
import tw.tripshare.localDateTime
import tw.tripshare.localMillis
import tw.tripshare.nowMillis
import tw.tripshare.today
import tw.tripshare.ui.ActionRow
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.AppShape
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.Looks
import tw.tripshare.ui.lifted
import tw.tripshare.ui.CenterMessage
import tw.tripshare.ui.CheckRow
import tw.tripshare.ui.Chip
import tw.tripshare.ui.ConfirmDialog
import tw.tripshare.ui.DateRangeDialog
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkDialog
import tw.tripshare.ui.InkField
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Page
import tw.tripshare.ui.SectionTitle
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.TimeDialog
import tw.tripshare.ui.Txt

private val MODES = listOf(SplitMode.equal to "平均", SplitMode.exact to "指定金額", SplitMode.shares to "份數")

/** Adds an expense when [expenseId] is null, otherwise edits or deletes it. */
@Composable
fun ExpenseFormScreen(tripId: String, expenseId: String?, date: String? = null) {
    val nav = LocalNav.current
    WithTrip(tripId) { data ->
        val existing = expenseId?.let { id -> data.expenses.firstOrNull { it.id == id } }
        // Taken once, so live updates from friends don't overwrite what's being typed.
        val initial = remember {
            when {
                expenseId == null -> {
                    // Abroad, most expenses are in the same foreign currency: start from the last one's.
                    val last = data.expenses.filter { runCatching { Currency.of(it.currency) }.isSuccess }
                        .maxByOrNull { maxOf(it.updatedAt, it.occurredAt) }
                    val cur = last?.currency ?: data.base.code
                    ExpenseDraft(
                        title = "", category = ExpenseCategory.food, amount = "", currency = cur,
                        base = data.base.code, rate = if (cur == data.base.code) "1" else last?.rateToBase.orEmpty(),
                        payerId = data.me?.id ?: data.members.first().id,
                        mode = SplitMode.equal, included = data.members.map { it.id }.toSet(), values = emptyMap(),
                        occurredAt = startTime(date), note = "",
                    )
                }
                existing != null -> ExpenseDraft.of(existing, data.base.code)
                else -> null
            }
        }
        if (initial == null) {
            Page("編輯", onBack = { nav.pop() }) { CenterMessage("這筆已經被刪掉了。") }
        } else {
            ExpenseForm(data, initial, isNew = expenseId == null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseForm(data: TripData, initial: ExpenseDraft, isNew: Boolean) {
    val session = LocalSession.current
    val nav = LocalNav.current
    val toasts = LocalToasts.current
    val scope = rememberCoroutineScope()
    val base = data.base.code

    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.category) }
    // What's typed on the keypad, e.g. "1280×2"; the amount is its result.
    var expr by remember { mutableStateOf(initial.amount.replace(",", "")) }
    var keypad by remember { mutableStateOf(isNew) }
    val focus = LocalFocusManager.current
    var currency by remember { mutableStateOf(initial.currency) }
    var rate by remember { mutableStateOf(initial.rate) }
    var rateNote by remember { mutableStateOf<String?>(null) }
    var fetchingRate by remember { mutableStateOf(false) }
    var payerId by remember { mutableStateOf(initial.payerId) }
    var mode by remember { mutableStateOf(initial.mode) }
    val included = remember { mutableStateMapOf<String, Boolean>().apply { initial.included.forEach { put(it, true) } } }
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(initial.values) } }
    val start = remember { localDateTime(initial.occurredAt) }
    var date by remember { mutableStateOf(start.date) }
    var time by remember { mutableStateOf(hm(start.hour, start.minute)) }
    var note by remember { mutableStateOf(initial.note) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }

    fun edited(block: () -> Unit) { block(); error = null }

    /** "11/4（三） · 第 2 天", the trip day only when the date falls inside the trip. */
    fun dayText(d: LocalDate) = shortDate(ymd(d)) +
        (tripDayNumber(d, data.trip.startDate, data.trip.endDate)?.let { " · 第 $it 天" } ?: "")

    /** Switches to [code] and looks up today's rate; [keep] stays when the lookup fails. */
    fun onCurrency(code: String, keep: String = "") {
        currency = code
        rateNote = null
        if (code == base) { rate = "1"; return }
        fetchingRate = true
        scope.launch {
            val r = session.rates.rate(code, base)
            if (currency != code) return@launch
            fetchingRate = false
            if (r != null) {
                rate = r
                rateNote = "今日參考匯率，可以改成刷卡實際匯率"
            } else if (keep.isNotEmpty()) {
                rate = keep
                rateNote = "查不到今天的匯率，先用上一筆的"
            } else {
                rate = ""
                rateNote = "查不到匯率（沒網路？），請手動輸入"
            }
        }
    }

    LaunchedEffect(Unit) { if (isNew && currency != base) onCurrency(currency, keep = rate) }

    val cur = Currency.of(currency)
    val amountMinor = Calc.minor(expr, cur)

    fun save() {
        if (saving) return
        val hmv = parseHm(time)
        if (hmv == null) { error = "時間格式要像 18:30"; return }
        val draft = initial.copy(
            title = title, category = category, amount = amountMinor?.let { cur.format(it) } ?: expr, currency = currency, rate = rate,
            payerId = payerId, mode = mode, included = included.filterValues { it }.keys,
            values = values.toMap(), note = note,
            occurredAt = localMillis(LocalDateTime(date.year, date.month, date.day, hmv.first, hmv.second)),
        )
        val e = try {
            draft.build(data.members.map { it.id })
        } catch (x: SplitError) {
            error = x.message
            return
        }
        saving = true
        session.background.launch {
            runCatching { session.repo.saveExpense(data.trip.id, e) }.onFailure { toasts.show("沒有存成功：${it.message}") }
        }
        nav.pop()
    }

    Page(
        if (isNew) "記一筆" else "編輯",
        onBack = { nav.pop() },
        actions = { if (!isNew) TextAction("刪除", { picking = "delete" }, color = AppColors.error) },
        bottom = if (keypad) ({ Keypad(cur.decimals, { k -> edited { expr = Calc.press(expr, k, cur.decimals) } }, { keypad = false }) }) else null,
    ) {
        AmountBox(
            expr, amountMinor, cur,
            converted = if (currency != base && amountMinor != null) converted(cur.format(amountMinor), currency, base, rate) else null,
            base = base,
            active = keypad,
            onTap = { focus.clearFocus(); keypad = true },
            onCurrency = { picking = "currency" },
        )
        InkField(title, { edited { title = it } }, "項目，例如「一蘭拉麵」", maxLength = 40, onFocus = { keypad = false })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseCategory.entries.forEach { c -> Chip(c.label, category == c, { category = c }) }
        }
        // Near the top, so filling in yesterday's receipts doesn't need a scroll.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InkButton(dayText(date), { focus.clearFocus(); keypad = false; picking = "date" }, Modifier.weight(1f), fill = AppColors.paper)
            InkButton(time, { focus.clearFocus(); keypad = false; picking = "time" }, Modifier.width(110.dp), fill = AppColors.paper)
        }
        if (currency != base) {
            InkField(
                rate, { edited { rate = it } }, "匯率：1 $currency = ? $base" + if (fetchingRate) "（查詢中…）" else "",
                maxLength = 18, keyboard = KeyboardType.Decimal, onFocus = { keypad = false },
            )
            rateNote?.let { Txt(it, size = 13, color = AppColors.faint) }
        }
        Txt("誰付的", size = 14, color = AppColors.faint)
        InkButton(data.nameOf(payerId), { picking = "payer" }, Modifier.fillMaxWidth(), fill = AppColors.paper)

        SectionTitle("怎麼分")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MODES.forEach { (m, label) ->
                Chip(label, mode == m, {
                    edited {
                        mode = m
                        if (m == SplitMode.shares) data.members.forEach { if (values[it.id]?.toLongOrNull() == null) values[it.id] = "1" }
                    }
                }, Modifier.weight(1f), color = AppColors.yamabuki)
            }
        }
        data.members.forEach { m ->
            val on = included[m.id] == true
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CheckRow(m.name, on, { edited { included[m.id] = !on } }, Modifier.weight(1f))
                if (mode != SplitMode.equal && on) {
                    InkField(
                        values[m.id].orEmpty(), { edited { values[m.id] = it } },
                        if (mode == SplitMode.exact) currency else "份", Modifier.width(120.dp),
                        maxLength = 15, keyboard = if (mode == SplitMode.exact) KeyboardType.Decimal else KeyboardType.Number,
                        onFocus = { keypad = false },
                    )
                }
            }
        }

        InkField(note, { note = it }, "備註（可不填）", maxLength = 200, onFocus = { keypad = false })
        error?.let { Txt(it, color = AppColors.error) }
        InkButton("儲存", ::save, Modifier.fillMaxWidth(), enabled = !saving)
    }

    when (picking) {
        "currency" -> InkDialog("幣別", { picking = null }, buttons = { TextAction("取消", { picking = null }) }) {
            Currency.all.forEach { c -> ActionRow("${c.name} ${c.code}") { picking = null; onCurrency(c.code) } }
        }
        "time" -> {
            val (h, m) = parseHm(time) ?: (12 to 0)
            TimeDialog(h, m, onDone = { hh, mm -> edited { time = hm(hh, mm) }; picking = null }, onDismiss = { picking = null })
        }
        "payer" -> InkDialog("誰付的", { picking = null }, buttons = { TextAction("取消", { picking = null }) }) {
            data.members.forEach { m -> ActionRow(m.name) { payerId = m.id; picking = null } }
        }
        "date" -> DateRangeDialog(
            date, date, today(),
            onDone = { d: LocalDate, _: LocalDate -> date = d; picking = null },
            onDismiss = { picking = null },
            single = true,
        )
        "delete" -> ConfirmDialog("刪除這筆？", "刪掉後大家的結算會跟著改。", "刪除", onConfirm = {
            val id = initial.id
            session.background.launch {
                runCatching { session.repo.deleteExpense(data.trip.id, id) }.onFailure { toasts.show("沒有刪除成功：${it.message}") }
            }
            nav.pop()
        }, onDismiss = { picking = null })
    }
}

/**
 * When a new expense starts: now, or the current time of day on [date]
 * ("yyyy-MM-dd", from a day's ＋ on the 記帳 tab).
 */
private fun startTime(date: String?): Long {
    val now = nowMillis()
    val d = date?.let { runCatching { parseYmd(it) }.getOrNull() } ?: return now
    val t = localDateTime(now)
    return localMillis(LocalDateTime(d.year, d.month, d.day, t.hour, t.minute))
}

/** [amount] of [from] in [to] at [rate], formatted, or null while the inputs aren't valid. */
private fun converted(amount: String, from: String, to: String, rate: String): String? {
    val f = Currency.of(from)
    val t = Currency.of(to)
    val a = f.parse(amount) ?: return null
    val r = Rate.tryParse(rate) ?: return null
    return runCatching { t.format(convert(a, f, t, r)) }.getOrNull()
}

/**
 * The amount, big: what's typed (when it's a sum), the result, and the result
 * in the trip's currency. Tapping it brings the keypad back.
 */
@Composable
private fun AmountBox(
    expr: String,
    minor: Long?,
    cur: Currency,
    converted: String?,
    base: String,
    active: Boolean,
    onTap: () -> Unit,
    onCurrency: () -> Unit,
) {
    InkCard(Modifier.fillMaxWidth(), onClick = onTap) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("金額", Modifier.weight(1f), size = 14, color = AppColors.faint)
            Chip("${cur.code} ▾", true, onCurrency, color = AppColors.yamabuki)
        }
        if (Calc.hasOperator(expr)) Txt(groupedExpr(expr), size = 16, color = AppColors.faint, maxLines = 1)
        Txt(
            when {
                expr.isEmpty() -> "0"
                minor != null -> grouped(cur.format(minor))
                else -> groupedExpr(expr)
            },
            size = 40, bold = true, maxLines = 1,
            color = if (expr.isEmpty() || !active && minor == null) AppColors.faint else AppColors.ink,
        )
        if (converted != null) Txt("≈ ${grouped(converted)} $base", size = 16, bold = true, color = AppColors.coral)
    }
}

/** Thousands commas in each number of a sum, e.g. "12800+3500.5" → "12,800+3,500.5". */
private fun groupedExpr(expr: String): String = NUMBER.replace(expr) { grouped(it.value) }

private val NUMBER = Regex("""\d+(\.\d*)?""")

/** The calculator keys, in place of the phone's keyboard while typing the amount. */
@Composable
private fun Keypad(decimals: Int, onKey: (String) -> Unit, onDone: () -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(if (decimals > 0) "." else "C", "0", "⌫", "+"),
    )
    Column(
        Modifier.fillMaxWidth().background(AppColors.card).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val op = k in Calc.OPS
                    Box(
                        Modifier.weight(1f).height(50.dp).lifted(AppShape.small, elevation = 1.dp).clip(AppShape.small)
                            .background(if (op) AppColors.yamabuki else AppColors.paper, AppShape.small)
                            .clickable(role = Role.Button, onClickLabel = k) { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) { Txt(k, size = 22, bold = true, color = if (op && !Looks.scrapbook) AppColors.coral else AppColors.ink) }
                }
            }
        }
        InkButton("完成", onDone, Modifier.fillMaxWidth())
    }
}
