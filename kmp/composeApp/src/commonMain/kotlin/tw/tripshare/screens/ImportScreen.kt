package tw.tripshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import tw.tripshare.copyToClipboard
import tw.tripshare.core.Flight
import tw.tripshare.core.ItineraryItem
import tw.tripshare.core.Lodging
import tw.tripshare.core.Transport
import tw.tripshare.core.parseYmd
import tw.tripshare.core.sheet.ExpenseChoices
import tw.tripshare.core.sheet.ImportContext
import tw.tripshare.core.sheet.ImportResult
import tw.tripshare.core.sheet.KNOWN_HEADERS
import tw.tripshare.core.sheet.RateSource
import tw.tripshare.core.sheet.Settled
import tw.tripshare.core.sheet.aiPrompt
import tw.tripshare.core.sheet.analyze
import tw.tripshare.core.sheet.analyzeRows
import tw.tripshare.core.sheet.SheetError
import tw.tripshare.core.sheet.SheetInfo
import tw.tripshare.core.sheet.SheetRef
import tw.tripshare.core.sheet.parseSheetLink
import tw.tripshare.data.GoogleSheets
import tw.tripshare.core.sheet.autoMatch
import tw.tripshare.core.sheet.currencyFor
import tw.tripshare.core.sheet.mentionedNames
import tw.tripshare.core.sheet.nameKey
import tw.tripshare.core.sheet.settle
import tw.tripshare.core.shortDate
import tw.tripshare.localMillis
import tw.tripshare.secureRandomBytes
import tw.tripshare.ui.AppColors
import tw.tripshare.ui.AppShape
import tw.tripshare.ui.Chip
import tw.tripshare.ui.ConfirmDialog
import tw.tripshare.ui.InkButton
import tw.tripshare.ui.InkCard
import tw.tripshare.ui.InkField
import tw.tripshare.ui.LocalNav
import tw.tripshare.ui.LocalToasts
import tw.tripshare.ui.Page
import tw.tripshare.ui.SectionTitle
import tw.tripshare.ui.TextAction
import tw.tripshare.ui.Txt

/** A name the sheet uses becomes a new member of the trip. */
private const val NEW_MEMBER = "+new"

private enum class Step { Paste, Preview, Done }

/**
 * 從試算表匯入: paste a range copied from Google Sheets or Excel (or an AI
 * chat's tables), check what was read, then write it all in one go, with
 * a way to take the whole import back.
 */
/** What the preview reads: pasted text, or one tab read from a shared link. */
private sealed interface Input {
    data class Text(val text: String) : Input
    data class Tab(val title: String, val rows: List<List<String>>) : Input
}

/** The shared-link part of the first step, kept while going back and forth. */
private class LinkState {
    var link by mutableStateOf("")
    var ref by mutableStateOf<SheetRef?>(null)
    var info by mutableStateOf<SheetInfo?>(null)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
}

@Composable
fun ImportScreen(tripId: String) {
    val nav = LocalNav.current
    WithTrip(tripId) { data ->
        var step by remember { mutableStateOf(Step.Paste) }
        var text by remember { mutableStateOf("") }
        var input by remember { mutableStateOf<Input?>(null) }
        val linkState = remember { LinkState() }
        var importId by remember { mutableStateOf<String?>(null) }
        var summary by remember { mutableStateOf("") }
        val ctx = ImportContext(data.trip.startDate, data.trip.endDate, data.trip.baseCurrency, data.members.map { it.name })
        val back: () -> Unit = { if (step == Step.Preview) step = Step.Paste else nav.pop() }
        Page("從試算表匯入", onBack = back) {
            when (step) {
                Step.Paste -> {
                    LinkPart(linkState) { title, rows -> input = Input.Tab(title, rows); step = Step.Preview }
                    PastePart(data, ctx, text, { text = it }) { input = Input.Text(text); step = Step.Preview }
                }
                Step.Preview -> {
                    val i = input!!
                    if (i is Input.Tab) Txt("分頁「${i.title}」", size = 14, color = AppColors.faint)
                    val result = remember(i, ctx) {
                        when (i) {
                            is Input.Text -> analyze(i.text, ctx)
                            is Input.Tab -> analyzeRows(i.rows, ctx, i.title)
                        }
                    }
                    PreviewStep(data, ctx, result) { id, said ->
                        importId = id
                        summary = said
                        step = Step.Done
                    }
                }
                Step.Done -> DoneStep(data, importId!!, summary)
            }
        }
    }
}

/** Paste a sheet's link, pick a tab. */
@Composable
private fun LinkPart(st: LinkState, onTab: (String, List<List<String>>) -> Unit) {
    val scope = rememberCoroutineScope()
    fun run(block: suspend () -> Unit) {
        if (st.busy) return
        st.busy = true
        st.error = null
        scope.launch {
            try { block() } catch (e: SheetError) { st.error = e.message } catch (e: Exception) { st.error = "讀取失敗：${e.message}" }
            st.busy = false
        }
    }
    SectionTitle("用試算表連結")
    InkCard(Modifier.fillMaxWidth(), fill = AppColors.mizu) {
        Txt("貼上 Google 試算表的連結，選一個分頁就能匯入。", size = 15)
        Txt("試算表要先按「共用」，把一般存取權設成「知道連結的任何人」可以檢視。", size = 14, color = AppColors.faint)
    }
    InkField(st.link, { st.link = it; st.error = null }, "試算表連結", maxLength = 500)
    InkButton(if (st.busy && st.info == null) "讀取中…" else "讀取分頁", {
        val ref = parseSheetLink(st.link)
        if (ref == null) { st.error = "這看起來不是 Google 試算表的連結"; return@InkButton }
        run {
            st.info = null
            st.info = GoogleSheets.tabs(ref.id)
            st.ref = ref
        }
    }, Modifier.fillMaxWidth(), enabled = st.link.isNotBlank() && !st.busy)
    st.error?.let { Txt(it, size = 14, color = AppColors.error) }
    val info = st.info
    val ref = st.ref
    if (info != null && ref != null) {
        Txt("「${info.title}」有 ${info.tabs.size} 個分頁，選一個：", size = 14, color = AppColors.faint)
        val linked = info.tabs.firstOrNull { it.gid == ref.gid }
        (listOfNotNull(linked) + info.tabs.filter { it != linked }).forEach { tab ->
            InkCard(Modifier.fillMaxWidth(), fill = if (tab == linked) AppColors.yamabuki else AppColors.paper, onClick = {
                run { onTab(tab.title, GoogleSheets.values(ref.id, tab.title)) }
            }) {
                Txt(tab.title, bold = true)
                if (tab == linked) Txt("你貼的連結開的是這個分頁", size = 13, color = AppColors.faint)
            }
        }
        if (st.busy) Txt("讀取中…", size = 14, color = AppColors.faint)
    }
}

@Composable
private fun PastePart(data: TripData, ctx: ImportContext, text: String, onText: (String) -> Unit, onNext: () -> Unit) {
    val toasts = LocalToasts.current
    var showHeaders by remember { mutableStateOf(false) }
    SectionTitle("或複製貼上")
    InkCard(Modifier.fillMaxWidth()) {
        Txt("在 Google 試算表或 Excel 選取範圍、複製，貼在下面按「讀取」。Excel 檔或不想公開的試算表用這個。", size = 15)
        Txt("可以一次選花費表加行程，總計、平均這類彙總會自動略過。匯入前會先讓你看過。", size = 14, color = AppColors.faint)
    }
    InkField(text, onText, "貼上的內容", maxLength = 200_000, lines = 6)
    InkButton("讀取", onNext, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
    TextAction(if (showHeaders) "收起" else "看看認得哪些表頭", { showHeaders = !showHeaders })
    if (showHeaders) InkCard(Modifier.fillMaxWidth()) { Txt(KNOWN_HEADERS, size = 14) }

    SectionTitle("格式太亂？用 AI 轉換")
    InkCard(Modifier.fillMaxWidth()) {
        Txt("把提示詞和試算表一起貼到你自己的 Claude 或 ChatGPT，再把它回覆的表格整段貼回上面。", size = 15)
        Txt("App 不會把資料傳給任何 AI，要不要貼由你決定。", size = 14, color = AppColors.faint)
        InkButton("複製提示詞", {
            copyToClipboard(aiPrompt(ctx, data.trip.name))
            toasts.show("已複製提示詞")
        }, Modifier.fillMaxWidth(), fill = AppColors.yamabuki)
    }
}

/** Ticks per row, keyed like "e3" (expense 3) or "i0" (stop 0); absent means ticked. */
private class Ticks {
    val off = mutableStateMapOf<String, Boolean>()
    fun on(key: String) = off[key] != true
    fun toggle(key: String) { off[key] = on(key) }
}

@Composable
private fun PreviewStep(data: TripData, ctx: ImportContext, r: ImportResult, onDone: (String, String) -> Unit) {
    val session = LocalSession.current
    val toasts = LocalToasts.current
    val repo = session.repo
    val base = data.trip.baseCurrency

    if (r.isEmpty) {
        InkCard(Modifier.fillMaxWidth(), fill = AppColors.yamabuki) {
            Txt("沒有讀到可以匯入的資料", bold = true)
            Txt("請確認範圍有包含表頭（例如「項目、金額」或第一列是日期），或改用 AI 轉換。", size = 14)
        }
        SkippedList(r)
        return
    }

    val ticks = remember(r) { Ticks() }
    // Name as written → member id, or NEW_MEMBER.
    val names = remember(r) {
        mutableStateMapOf<String, String>().apply {
            putAll(autoMatch(mentionedNames(r.expenses), data.members.map { it.id to it.name }))
        }
    }
    val newIds = remember(r) { mutableMapOf<String, String>() }
    var defaultCurrency by remember(r) { mutableStateOf(base) }
    // Most sheets leave the payer blank; the person importing is the likeliest guess.
    var defaultPayer by remember(r) { mutableStateOf(data.me?.id) }
    val rates = remember(r) { mutableStateMapOf<String, String>() }
    var confirming by remember { mutableStateOf(false) }

    val mentioned = mentionedNames(r.expenses)
    val newMembers = mentioned.filter { names[nameKey(it)] == NEW_MEMBER }
        .map { n -> newIds.getOrPut(nameKey(n)) { repo.newMemberId(data.trip.id) } to n }
    val idOf = names.mapValues { (k, v) -> if (v == NEW_MEMBER) newIds[k] ?: "" else v }.filterValues { it.isNotEmpty() }
    val choices = ExpenseChoices(
        defaultCurrency = defaultCurrency,
        defaultPayerId = defaultPayer,
        names = idOf,
        rates = rates,
        memberOrder = data.members.map { it.id } + newMembers.map { it.first },
    )
    val noonOf = { ymd: String -> parseYmd(ymd).let { localMillis(LocalDateTime(it.year, it.month, it.day, 12, 0)) } }
    val settled = r.expenses.map { it.settle(ctx, choices, noonOf) }

    // Currencies that need a rate because some row has no 折合 amount.
    val needRate = r.expenses.filter { it.baseNumber == null && it.problem == null }
        .map { it.currencyFor(choices) }.filter { it != base }.distinct()
    LaunchedEffect(needRate) {
        for (code in needRate) if (rates[code] == null) session.rates.rate(code, base)?.let { rates[code] = it }
    }

    val outside = r.datesOutside(ctx)
    if (outside.isNotEmpty()) {
        val start = minOf(outside.first(), ctx.tripStart)
        val end = maxOf(outside.last(), ctx.tripEnd)
        InkCard(Modifier.fillMaxWidth(), fill = AppColors.yamabuki) {
            Txt("有 ${outside.size} 個日期不在旅程期間內", bold = true)
            Txt("例如 ${shortDate(outside.first())}。不改的話，那幾天的行程在行程頁看不到。", size = 14)
            InkButton("把旅程改成 ${shortDate(start)} – ${shortDate(end)}", {
                session.background.launch {
                    runCatching { repo.updateTripDates(data.trip.id, start, end) }.onFailure { toasts.show("沒有改成功：${it.message}") }
                }
            }, Modifier.fillMaxWidth(), fill = AppColors.paper)
        }
    }

    if (r.expenses.isNotEmpty()) {
        if (mentioned.isNotEmpty()) {
            SectionTitle("名字對應")
            Txt("試算表上的名字是誰？", size = 14, color = AppColors.faint)
            mentioned.forEach { n ->
                InkCard(Modifier.fillMaxWidth()) {
                    Txt("「$n」", bold = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.members.forEach { m ->
                            Chip(m.name, names[nameKey(n)] == m.id, { names[nameKey(n)] = m.id })
                        }
                        if (data.isOwner) Chip("新增成員「$n」", names[nameKey(n)] == NEW_MEMBER, { names[nameKey(n)] = NEW_MEMBER }, color = AppColors.wakatake)
                    }
                }
            }
        }

        SectionTitle("沒寫的時候")
        InkCard(Modifier.fillMaxWidth()) {
            Txt("沒寫幣別的金額是", size = 14, color = AppColors.faint)
            val seen = (listOf(base) + r.expenses.mapNotNull { it.namedCurrency } + listOf("JPY", "KRW", "THB", "USD", "EUR")).distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                seen.forEach { c -> Chip(c, defaultCurrency == c, { defaultCurrency = c }) }
            }
            Txt("沒寫支付人的是誰付的", size = 14, color = AppColors.faint)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("不匯入", defaultPayer == null, { defaultPayer = null })
                data.members.forEach { m -> Chip(m.name, defaultPayer == m.id, { defaultPayer = m.id }) }
            }
        }

        if (needRate.isNotEmpty()) {
            SectionTitle("匯率")
            Txt("有「折合$base」金額的列，直接用試算表算出的匯率；其他的用這裡的匯率。", size = 14, color = AppColors.faint)
            needRate.forEach { code ->
                InkField(rates[code].orEmpty(), { rates[code] = it.trim() }, "1 $code = ? $base（今日參考匯率，可改）")
            }
        }

        SectionTitle("花費 ${r.expenses.size} 筆")
        Txt("全部預設所有成員平分，匯入後可以一筆筆再改。", size = 14, color = AppColors.faint)
        r.expenses.forEachIndexed { i, row ->
            val s = settled[i]
            val key = "e$i"
            val subtitle = when (s) {
                is Settled.Ready -> {
                    val payer = data.members.firstOrNull { it.id == s.expense.payerId }?.name
                        ?: newMembers.firstOrNull { it.first == s.expense.payerId }?.second ?: "?"
                    val who = if (s.expense.splits.size == choices.memberOrder.size) "大家分" else "${s.expense.splits.size} 人分"
                    val rateText = when (s.rateSource) {
                        RateSource.same -> ""
                        RateSource.sheet -> " · 匯率 ${s.expense.rateToBase}（試算表）"
                        RateSource.chosen -> " · 匯率 ${s.expense.rateToBase}"
                    }
                    listOfNotNull(row.date?.let(::shortDate), "$payer 付", who, s.expense.category.label).joinToString(" · ") + rateText
                }
                is Settled.Problem -> s.reason
            }
            val amount = row.money?.let { m -> "${m.number} ${row.currencyFor(choices)}" } ?: ""
            PickRow(
                title = row.title.ifEmpty { "（第 ${row.line} 列）" },
                subtitle = subtitle,
                trailing = amount,
                checked = s is Settled.Ready && ticks.on(key),
                enabled = s is Settled.Ready,
                warn = s is Settled.Problem,
                onToggle = { ticks.toggle(key) },
            )
        }
    }

    if (r.items.isNotEmpty()) {
        SectionTitle("行程 ${r.items.size} 個")
        r.items.forEachIndexed { i, it ->
            PickRow(
                title = it.title,
                subtitle = listOfNotNull(shortDate(it.date), it.time, it.transport.takeIf { t -> t != Transport.none }?.label, it.note?.lines()?.first())
                    .joinToString(" · "),
                checked = ticks.on("i$i"),
                onToggle = { ticks.toggle("i$i") },
            )
        }
    }
    if (r.lodgings.isNotEmpty()) {
        SectionTitle("住宿 ${r.lodgings.size} 筆")
        r.lodgings.forEachIndexed { i, it ->
            PickRow(it.name, "${shortDate(it.checkIn)} → ${shortDate(it.checkOut)}" + (it.note?.let { n -> " · $n" } ?: ""), checked = ticks.on("l$i"), onToggle = { ticks.toggle("l$i") })
        }
    }
    if (r.flights.isNotEmpty()) {
        SectionTitle("航班 ${r.flights.size} 班")
        r.flights.forEachIndexed { i, it ->
            PickRow("${it.flightNo} ${it.fromCode} → ${it.toCode}", "${shortDate(it.departDate)} ${it.departTime}", checked = ticks.on("f$i"), onToggle = { ticks.toggle("f$i") })
        }
    }
    if (r.checklist.isNotEmpty()) {
        SectionTitle("清單 ${r.checklist.size} 項")
        r.checklist.forEachIndexed { i, it ->
            PickRow(it.text, it.kind.label, checked = ticks.on("c$i"), onToggle = { ticks.toggle("c$i") })
        }
    }
    SkippedList(r)

    val expenses = settled.withIndex().filter { (i, s) -> s is Settled.Ready && ticks.on("e$i") }.map { (_, s) -> (s as Settled.Ready).expense }
    val items = r.items.withIndex().filter { ticks.on("i${it.index}") }.map { it.value }
    val lodgings = r.lodgings.withIndex().filter { ticks.on("l${it.index}") }.map { it.value }
    val flights = r.flights.withIndex().filter { ticks.on("f${it.index}") }.map { it.value }
    val checklist = r.checklist.withIndex().filter { ticks.on("c${it.index}") }.map { it.value }
    val usedIds = expenses.flatMap { e -> e.splits.keys + e.payerId }.toSet()
    val creating = newMembers.filter { it.first in usedIds }
    val parts = listOfNotNull(
        expenses.size.takeIf { it > 0 }?.let { "花費 $it 筆" },
        items.size.takeIf { it > 0 }?.let { "行程 $it 個" },
        lodgings.size.takeIf { it > 0 }?.let { "住宿 $it 筆" },
        flights.size.takeIf { it > 0 }?.let { "航班 $it 班" },
        checklist.size.takeIf { it > 0 }?.let { "清單 $it 項" },
        creating.size.takeIf { it > 0 }?.let { "新成員 $it 位" },
    )
    // Rows that can't be imported are greyed out above; say so next to the button too.
    val blocked = settled.filterIsInstance<Settled.Problem>()
    if (blocked.isNotEmpty()) {
        InkCard(Modifier.fillMaxWidth(), fill = AppColors.yamabuki) {
            Txt("有 ${blocked.size} 筆花費不會匯入", bold = true)
            val why = blocked.groupingBy { it.reason }.eachCount().entries.sortedByDescending { it.value }.take(3)
            Txt(why.joinToString("、") { "${it.key}（${it.value} 筆）" } + "。可以在上面的「名字對應」或「沒寫的時候」補上。", size = 14)
        }
    }
    InkButton(
        if (parts.isEmpty()) "沒有勾選要匯入的資料" else "匯入：${parts.joinToString("、")}",
        { confirming = true },
        Modifier.fillMaxWidth(),
        enabled = parts.isNotEmpty(),
    )

    if (confirming) {
        ConfirmDialog(
            "確定匯入？",
            "${parts.joinToString("、")}會加進「${data.trip.name}」，所有成員都看得到。匯入後可以整批復原。",
            "匯入",
            onConfirm = {
                val id = secureRandomBytes(6).joinToString("") { b -> (b.toInt() and 0xff).toString(16).padStart(2, '0') }
                val firstOrder = (data.members.maxOfOrNull { it.order } ?: -1) + 1
                session.background.launch {
                    runCatching {
                        repo.importAll(
                            data.trip.id, id, creating, firstOrder, expenses,
                            items.map { ItineraryItem("", it.date, it.title, 0, it.time, it.place, null, it.note, it.transport) },
                            lodgings.map { Lodging("", it.name, it.checkIn, it.checkOut, it.address, null, it.confirmation, it.note) },
                            flights.map { Flight("", it.flightNo, it.fromCode, it.toCode, it.departDate, it.departTime, it.airline, null, null, it.arriveDate, it.arriveTime, it.note) },
                            checklist.map { it.kind to it.text },
                        )
                    }.onFailure { toasts.show("匯入失敗：${it.message}") }
                }
                onDone(id, parts.joinToString("、"))
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun SkippedList(r: ImportResult) {
    val skipped = r.skipped.map { "${it.what}「${it.text.lines().first().take(30)}」：${it.reason}" }
    if (skipped.isEmpty()) return
    SectionTitle("略過 ${skipped.size} 列")
    InkCard(Modifier.fillMaxWidth()) { skipped.forEach { Txt(it, size = 14) } }
}

@Composable
private fun DoneStep(data: TripData, importId: String, summary: String) {
    val nav = LocalNav.current
    val session = LocalSession.current
    val toasts = LocalToasts.current
    var undone by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    InkCard(Modifier.fillMaxWidth(), fill = if (undone) AppColors.paper else AppColors.wakatake) {
        Txt(if (undone) "已復原這次匯入" else "匯入完成", size = 20, bold = true)
        Txt(summary)
        if (!undone) Txt("沒網路的話，會在連上後自動同步。", size = 14, color = AppColors.faint)
    }
    InkButton("回到旅程", { nav.pop() }, Modifier.fillMaxWidth())
    if (!undone) TextAction("復原這次匯入", { confirming = true }, color = AppColors.error)
    if (confirming) {
        ConfirmDialog(
            "復原這次匯入？",
            "這次匯入的資料會全部刪掉（匯入後改過的不會刪）。",
            "復原",
            onConfirm = {
                undone = true
                session.background.launch {
                    runCatching { session.repo.undoImport(data.trip.id, importId) }.onFailure { toasts.show("復原失敗：${it.message}") }
                }
            },
            onDismiss = { confirming = false },
        )
    }
}

/** A tickable preview line; [warn] lines can't be ticked and say why in [subtitle]. */
@Composable
private fun PickRow(
    title: String,
    subtitle: String,
    trailing: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    warn: Boolean = false,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(AppShape.small)
            .border(2.dp, if (warn) AppColors.error else AppColors.ink, AppShape.small)
            .background(if (warn) Color(0xFFFBE3E1) else AppColors.white, AppShape.small)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(24.dp).border(2.dp, if (enabled) AppColors.ink else AppColors.faint, AppShape.tiny)
                .background(if (checked) AppColors.wakatake else AppColors.white, AppShape.tiny),
            contentAlignment = Alignment.Center,
        ) { if (checked) Txt("✓", size = 14, bold = true) }
        Column(Modifier.weight(1f)) {
            Txt(title, bold = true, maxLines = 2, color = if (enabled) AppColors.ink else AppColors.faint)
            if (subtitle.isNotEmpty()) Txt(subtitle, size = 13, color = if (warn) AppColors.error else AppColors.faint, maxLines = 2)
        }
        if (trailing.isNotEmpty()) Txt(trailing, size = 15, bold = true, align = TextAlign.End)
    }
}
