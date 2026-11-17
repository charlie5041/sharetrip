package tw.tripshare.core

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToLong

/**
 * What the expense form holds, as typed. [build] turns it into an
 * [Expense] or throws [SplitError] with a message for the person.
 */
data class ExpenseDraft(
    val title: String,
    val category: ExpenseCategory,
    val amount: String,
    val currency: String,
    val base: String,
    val rate: String,
    val payerId: String,
    val mode: SplitMode,
    val included: Set<String>,
    /** memberId → typed amount (exact) or share count (shares). Ignored for equal. */
    val values: Map<String, String>,
    val occurredAt: Long,
    val note: String,
    val id: String = "",
    val createdBy: String = "",
) {
    /** [order] is the members' ids in display order; splits keep that order. */
    fun build(order: List<String>): Expense {
        val cur = Currency.of(currency)
        if (title.isBlank()) throw SplitError("請填項目名稱")
        val amountMinor = cur.parse(amount)
        if (amountMinor == null || amountMinor == 0L) throw SplitError("金額格式不正確")
        val rateText = if (currency == base) "1" else rate.trim()
        if (Rate.tryParse(rateText) == null) throw SplitError("匯率格式不正確")
        val ids = order.filter { it in included }
        if (ids.isEmpty()) throw SplitError("至少要有一個人分攤")

        val splits = LinkedHashMap<String, Long>()
        for (id in ids) {
            val typed = values[id].orEmpty()
            splits[id] = when (mode) {
                SplitMode.equal -> 1
                SplitMode.shares -> typed.trim().toLongOrNull()?.takeIf { it > 0 } ?: throw SplitError("份數要是正整數")
                SplitMode.exact -> cur.parse(typed) ?: throw SplitError("請填每個人的金額")
            }
        }
        val e = Expense(
            id = id,
            title = title.trim(),
            category = category,
            amountMinor = amountMinor,
            currency = currency,
            rateToBase = rateText,
            payerId = payerId,
            splitMode = mode,
            splits = splits,
            occurredAt = occurredAt,
            createdBy = createdBy,
            updatedAt = 0,
            note = note.trim().ifEmpty { null },
        )
        sharesInOriginal(e, order) // Throws when exact amounts don't add up.
        return e
    }

    companion object {
        /** The form filled in from an existing expense, for editing. */
        fun of(e: Expense, base: String): ExpenseDraft {
            val cur = Currency.of(e.currency)
            return ExpenseDraft(
                title = e.title,
                category = e.category,
                amount = cur.format(e.amountMinor),
                currency = e.currency,
                base = base,
                rate = e.rateToBase,
                payerId = e.payerId,
                mode = e.splitMode,
                included = e.splits.keys,
                values = e.splits.mapValues { (_, v) -> if (e.splitMode == SplitMode.exact) cur.format(v) else v.toString() },
                occurredAt = e.occurredAt,
                note = e.note.orEmpty(),
                id = e.id,
                createdBy = e.createdBy,
            )
        }
    }
}

/**
 * A rate as a decimal string with six significant digits, plenty for a
 * trip and readable when stored ("0.213456", "31.2", "1"). Null for a
 * rate that isn't a positive finite number.
 */
fun formatRate(r: Double): String? {
    if (!r.isFinite() || r <= 0) return null
    var exp = floor(log10(r)).toInt()
    var digits = (r * pow10d(5 - exp)).roundToLong()
    if (digits >= 1_000_000) { digits /= 10; exp += 1 } // 9.999995 rounds up a place.
    val s = digits.toString() // Six digits, the first nonzero.
    val point = exp + 1 // Digits before the decimal point.
    val text = when {
        point <= 0 -> "0." + "0".repeat(-point) + s
        point >= s.length -> s + "0".repeat(point - s.length)
        else -> s.substring(0, point) + "." + s.substring(point)
    }
    val trimmed = if ('.' in text) text.trimEnd('0').trimEnd('.') else text
    // Rates too small for Rate's 15 digits can't be stored anyway.
    return trimmed.takeIf { Rate.tryParse(it) != null }
}

private fun pow10d(n: Int): Double {
    var r = 1.0
    if (n >= 0) repeat(n) { r *= 10 } else repeat(-n) { r /= 10 }
    return r
}

/** "18:30" → (18, 30), or null when it isn't a time of day. */
fun parseHm(text: String): Pair<Int, Int>? {
    val m = Regex("""(\d{1,2})[:：](\d{2})""").matchEntire(text.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) h to min else null
}

fun hm(hour: Int, minute: Int): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
