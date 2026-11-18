package tw.tripshare.core.sheet

import tw.tripshare.core.Currency
import tw.tripshare.core.Expense
import tw.tripshare.core.ExpenseCategory
import tw.tripshare.core.SplitMode
import tw.tripshare.core.formatRate

/** What the importer knows about the trip it imports into. */
data class ImportContext(
    val tripStart: String,
    val tripEnd: String,
    /** The trip's settlement currency. */
    val base: String,
    val memberNames: List<String>,
)

/** One expense row as read from the sheet, before names, defaults and rates are settled. */
data class ExpenseRow(
    /** Row number in the pasted block, from 1, to point at it. */
    val line: Int,
    val title: String,
    /** Null when the amount can't be read; [problem] says why. */
    val money: Money?,
    /** From a 幣別 column; wins over a symbol in the amount. */
    val currencyColumn: String?,
    /** The same amount in the trip's currency, from a 折合台幣 column. */
    val baseNumber: String?,
    val payer: String?,
    /** Null means everyone. */
    val splitNames: List<String>?,
    val date: String?,
    val category: ExpenseCategory,
    val note: String?,
    /** Why this row can't be imported as it stands. */
    val problem: String? = null,
) {
    /** The currency this row names itself, or null to use the chosen default. */
    val namedCurrency: String? get() = currencyColumn ?: money?.currency
}

private object H {
    val title = listOf("項目", "花費項目", "支出項目", "品項", "名稱", "內容", "消費項目", "item")
    val amount = listOf("金額", "費用", "價格", "花費", "原幣金額", "支出", "amount", "cost", "price")
    val currency = listOf("幣別", "幣種", "貨幣", "currency")
    val payer = listOf("支付人", "支出人", "付款人", "付款者", "付錢", "誰付", "代墊人", "付款", "payer", "paidby")
    val method = listOf("付費方式", "付款方式", "支付方式", "付款工具")
    val note = listOf("備註", "說明", "note", "notes", "memo")
    val date = listOf("日期", "date", "消費日期")
    val split = listOf("分攤", "分攤人", "分攤者", "參與", "參與者", "split")
    val category = listOf("分類", "類別", "category")
}

private const val EVERYONE_WORDS = "全部 全員 大家 所有人 all everyone 均分 平分 一起"

/** A header row that starts an expense table. */
internal fun isExpenseHeader(keys: List<String>): Boolean = keys.any { it in H.title } && keys.any { it in H.amount }

/** A base-amount header: "折合台幣", "台幣", "折合TWD", "台幣金額". */
private fun isBaseHeader(key: String, base: String): Boolean {
    if (key.startsWith("折合") || key.startsWith("換算")) return true
    val k = key.removeSuffix("金額")
    return k.isNotEmpty() && currencyOf(k) == base
}

/** Finds every expense table in [s]: a header row naming at least an item and an amount. */
internal fun findExpenses(s: Section, ctx: ImportContext): List<ExpenseRow> {
    val out = mutableListOf<ExpenseRow>()
    var r = 0
    while (r < s.rows.size) {
        val keys = s.rows[r].map(::headerKey)
        val titleCol = keys.indexOfFirst { it in H.title }
        val amountCols = keys.withIndex().filter { it.value in H.amount }.map { it.index }
        if (titleCol < 0 || amountCols.isEmpty()) { r++; continue }
        // The table is the unbroken run of header cells around the item column;
        // other tables often sit beside it on the same rows.
        var from = titleCol
        while (from > 0 && keys[from - 1].isNotEmpty()) from--
        var to = titleCol
        while (to + 1 < keys.size && keys[to + 1].isNotEmpty()) to++
        val run = from..to
        fun col(names: List<String>): Int? =
            run.filter { keys[it] in names }.sortedBy { if (it >= titleCol) it - titleCol else 100 + titleCol - it }.firstOrNull()
        val amountCol = amountCols.filter { it in run }.minByOrNull { if (it > titleCol) it else 100 + it } ?: run { r++; continue }
        val baseCol = run.filter { it != amountCol && isBaseHeader(keys[it], ctx.base) }
            .sortedBy { if (it > titleCol) it - titleCol else 100 + titleCol - it }.firstOrNull()
        val currencyCol = col(H.currency)
        val payerCol = col(H.payer)
        val methodCol = col(H.method)
        val noteCol = col(H.note)
        val dateCol = col(H.date)
        val splitCol = col(H.split)
        val categoryCol = col(H.category)
        val headerCurrency = headerBracket(s.cell(r, amountCol))?.let(::currencyOf)

        var row = r + 1
        while (row < s.rows.size) {
            val k = s.rows[row].map(::headerKey)
            if (k.getOrNull(titleCol) in H.title && amountCols.any { k.getOrNull(it) in H.amount }) break // Next table.
            val title = s.cell(row, titleCol)
            val amountText = s.cell(row, amountCol)
            row++
            if (title.isEmpty() && amountText.isEmpty()) continue
            if (isSummary(title)) continue
            fun at(c: Int?) = c?.let { s.cell(row - 1, it) }.orEmpty()
            val (money, moneyError) = if (amountText.isEmpty()) null to null else try {
                parseMoney(amountText).let { it.copy(currency = it.currency ?: headerCurrency) } to null
            } catch (e: MoneyError) { null to e.message }
            val currencyText = at(currencyCol)
            val currencyColumn = if (currencyText.isEmpty()) null else currencyOf(currencyText)
            val baseNumber = at(baseCol).takeIf { it.isNotEmpty() }?.let { runCatching { parseMoney(it) }.getOrNull() }
                ?.takeIf { it.currency == null || it.currency == ctx.base }?.number
            val splitText = at(splitCol)
            val splitNames = splitText.takeIf { it.isNotEmpty() && it.lowercase() !in EVERYONE_WORDS.split(' ') }
                ?.split(Regex("""[、,，/／&＆+＋\s]+"""))?.map { it.trim() }?.filter { it.isNotEmpty() }
            val note = listOf(at(methodCol), at(noteCol)).filter { it.isNotEmpty() }.joinToString("｜").ifEmpty { null }
            val problem = when {
                title.isEmpty() -> "沒有項目名稱"
                moneyError != null -> moneyError
                currencyText.isNotEmpty() && currencyColumn == null -> "不認得的幣別「$currencyText」"
                else -> null
            }
            out += ExpenseRow(
                line = row,
                title = title.lines().first().take(80),
                money = money,
                currencyColumn = currencyColumn,
                baseNumber = baseNumber,
                payer = at(payerCol).ifEmpty { null },
                splitNames = splitNames,
                date = at(dateCol).takeIf { it.isNotEmpty() }?.let { parseSheetDate(it, ctx.tripStart) },
                category = categoryOf(at(categoryCol)) ?: guessCategory(title + " " + (note ?: "")),
                note = listOfNotNull(title.lines().drop(1).joinToString(" ").ifEmpty { null }, note).joinToString("｜").ifEmpty { null },
                problem = problem,
            )
        }
        r = row
    }
    return out
}

private val SUMMARY = listOf("總計", "合計", "小計", "總和", "總額", "total", "sum", "平均")
private fun isSummary(title: String) = title.lowercase().replace(" ", "").let { t -> SUMMARY.any { t.startsWith(it) } }

// ---- Settling a row into an Expense ----------------------------------------

/** What the person decided on the preview screen. */
data class ExpenseChoices(
    /** For rows that name no currency. */
    val defaultCurrency: String,
    /** For rows with no payer; null leaves them unimportable. */
    val defaultPayerId: String?,
    /** Name as written (see [nameKey]) → member id. */
    val names: Map<String, String>,
    /** Currency → rate typed or fetched, for rows without a 折合 amount. */
    val rates: Map<String, String>,
    /** Member ids in display order; everyone splits unless the row says otherwise. */
    val memberOrder: List<String>,
)

/** How a name is compared: case and spaces don't matter. */
fun nameKey(name: String): String = name.trim().lowercase().replace(" ", "")

/** A row settled: the expense to write, or why it can't be. */
sealed interface Settled {
    /** [pending]: no amount on the sheet, imported as 0 to fill in later. */
    data class Ready(val expense: Expense, val rateSource: RateSource, val pending: Boolean = false) : Settled
    data class Problem(val reason: String) : Settled
}

enum class RateSource { same, sheet, chosen }

/** The currency [row] will be recorded in. */
fun ExpenseRow.currencyFor(c: ExpenseChoices): String = namedCurrency ?: c.defaultCurrency

/**
 * Turns [this] into an [Expense] with the person's choices applied.
 * [occurredAt] maps a "yyyy-MM-dd" to the stored time.
 */
fun ExpenseRow.settle(ctx: ImportContext, c: ExpenseChoices, occurredAt: (String) -> Long): Settled {
    problem?.let { return Settled.Problem(it) }
    val code = currencyFor(c)
    val cur = Currency.all.firstOrNull { it.code == code } ?: return Settled.Problem("不支援的幣別 $code")
    val base = Currency.of(ctx.base)
    // A row without an amount still saves typing its name; it comes in as 0.
    val minor = money?.let { toMinor(it.number, cur).first } ?: 0L
    if (minor < 0) return Settled.Problem("金額是負的")
    val pending = minor == 0L
    val payerId = payer?.let { c.names[nameKey(it)] } ?: if (payer == null) c.defaultPayerId else null
    if (payerId == null) return Settled.Problem(if (payer == null) "沒有填支付人" else "「$payer」還沒對應到成員")
    val ids = if (splitNames == null) c.memberOrder else {
        val mapped = splitNames.map { n -> c.names[nameKey(n)] ?: return Settled.Problem("分攤的「$n」還沒對應到成員") }.toSet()
        c.memberOrder.filter { it in mapped }
    }
    if (ids.isEmpty()) return Settled.Problem("沒有人分攤")
    val (rate, source) = when {
        code == ctx.base -> "1" to RateSource.same
        baseNumber != null && !pending -> {
            val (baseMinor, _) = toMinor(baseNumber, base)
            val r = formatRate(baseMinor.toDouble() / pow10(base.decimals) / (minor.toDouble() / pow10(cur.decimals)))
                ?: return Settled.Problem("折合金額算不出匯率")
            r to RateSource.sheet
        }
        else -> (c.rates[code]?.trim()?.takeIf { tw.tripshare.core.Rate.tryParse(it) != null }
            ?: return Settled.Problem("缺 $code 的匯率")) to RateSource.chosen
    }
    return Settled.Ready(
        Expense(
            id = "",
            title = title,
            category = category,
            amountMinor = minor,
            currency = code,
            rateToBase = rate,
            payerId = payerId,
            splitMode = SplitMode.equal,
            splits = ids.associateWith { 1L },
            occurredAt = occurredAt(date ?: ctx.tripStart),
            createdBy = "",
            updatedAt = 0,
            note = note,
        ),
        source,
        pending,
    )
}

private fun pow10(n: Int): Double { var r = 1.0; repeat(n) { r *= 10 }; return r }

/** Every name the rows mention, as written, first spelling wins. */
fun mentionedNames(rows: List<ExpenseRow>): List<String> =
    rows.flatMap { listOfNotNull(it.payer) + it.splitNames.orEmpty() }
        .distinctBy(::nameKey)

/** Names that match a member exactly (ignoring case and spaces). */
fun autoMatch(names: List<String>, members: List<Pair<String, String>>): Map<String, String> =
    names.mapNotNull { n -> members.firstOrNull { nameKey(it.second) == nameKey(n) }?.let { nameKey(n) to it.first } }.toMap()
