package tw.tripshare.core

/** Thrown when an expense can't produce a valid allocation. */
class SplitError(message: String) : Exception(message)

/**
 * Exact Long multiplication. Amounts and rates here fit comfortably, but a
 * typo like a 15-digit amount must fail loudly rather than wrap around.
 */
internal fun mul(a: Long, b: Long): Long {
    if (a == 0L || b == 0L) return 0
    val r = a * b
    if (r / b != a || (a == -1L && b == Long.MIN_VALUE) || (b == -1L && a == Long.MIN_VALUE)) {
        throw SplitError("金額太大，無法計算")
    }
    return r
}

internal fun add(a: Long, b: Long): Long {
    val r = a + b
    if (((a xor r) and (b xor r)) < 0) throw SplitError("金額太大，無法計算")
    return r
}

private fun pow10(n: Int): Long {
    var r = 1L
    repeat(n) { r = mul(r, 10) }
    return r
}

/** A decimal string ("0.2134") as an exact fraction, so money never goes through a double. */
class Rate private constructor(val numerator: Long, val denominator: Long) {
    companion object {
        private val PATTERN = Regex("""(\d+)(?:\.(\d+))?""")

        /** Null when [text] is not a positive decimal number. */
        fun tryParse(text: String): Rate? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            val frac = m.groupValues[2]
            val digits = (m.groupValues[1] + frac).trimStart('0')
            if (digits.isEmpty() || digits.length > 15 || frac.length > 15) return null
            return Rate(digits.toLong(), pow10(frac.length))
        }

        fun parse(text: String): Rate = tryParse(text) ?: throw SplitError("匯率格式不正確：$text")

        val one = Rate(1, 1)
    }
}

/**
 * Splits [total] in proportion to [weights] by largest remainder: every
 * share is rounded down, then the members whose shares lost the most get
 * one more unit each until the sum is exact. Ties go to whoever comes
 * first in [weights]' iteration order, which callers set to member order.
 */
fun allocate(total: Long, weights: Map<String, Long>): Map<String, Long> {
    if (total < 0) throw SplitError("金額不能是負數")
    val entries = weights.entries.filter { it.value > 0 }.map { it.key to it.value }
    if (entries.isEmpty()) throw SplitError("至少要有一個人分攤")
    val sumW = entries.fold(0L) { s, e -> add(s, e.second) }
    val out = LinkedHashMap<String, Long>()
    val remainders = LongArray(entries.size)
    var given = 0L
    entries.forEachIndexed { i, (key, w) ->
        val p = mul(total, w)
        val v = p / sumW
        out[key] = v
        remainders[i] = p % sumW
        given += v
    }
    // The leftover is smaller than the number of participants.
    val byRemainder = entries.indices.sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
    for (j in 0 until (total - given).toInt()) {
        val k = entries[byRemainder[j]].first
        out[k] = out.getValue(k) + 1
    }
    return out
}

/** Each participant's share of [e] in its own currency. [order] lists member ids in display order. */
fun sharesInOriginal(e: Expense, order: List<String>): Map<String, Long> {
    val ordered = LinkedHashMap<String, Long>()
    for (id in order) {
        val w = e.splits[id] ?: 0
        if (w > 0) ordered[id] = w
    }
    return when (e.splitMode) {
        SplitMode.equal -> allocate(e.amountMinor, ordered.mapValues { 1L })
        SplitMode.shares -> allocate(e.amountMinor, ordered)
        SplitMode.exact -> {
            val sum = ordered.values.fold(0L, ::add)
            if (sum != e.amountMinor) throw SplitError("各人金額加總（$sum）不等於總額（${e.amountMinor}）")
            if (ordered.isEmpty()) throw SplitError("至少要有一個人分攤")
            ordered
        }
    }
}

/** [amountMinor] of [from] converted to [to] minor units at [rate], rounded half up. */
fun convert(amountMinor: Long, from: Currency, to: Currency, rate: Rate): Long {
    val scale = to.decimals - from.decimals
    var num = mul(amountMinor, rate.numerator)
    var den = rate.denominator
    if (scale >= 0) num = mul(num, pow10(scale)) else den = mul(den, pow10(-scale))
    return add(mul(num, 2), den) / mul(den, 2)
}

/** [e]'s total in the trip's [base] currency. */
fun baseTotal(e: Expense, base: Currency): Long {
    val from = Currency.of(e.currency)
    val rate = if (e.currency == base.code) Rate.one else Rate.parse(e.rateToBase)
    return convert(e.amountMinor, from, base, rate)
}

/**
 * What [e] costs each participant in the trip's [base] currency.
 *
 * The total is converted once and then split in proportion to the shares
 * in the original currency, so the payer is credited exactly what the
 * participants are charged; converting each share on its own could leave
 * them a unit apart.
 */
fun sharesInBase(e: Expense, base: Currency, order: List<String>): Map<String, Long> {
    val original = sharesInOriginal(e, order)
    val totalBase = baseTotal(e, base)
    if (totalBase == 0L) return original.mapValues { 0L }
    return allocate(totalBase, original)
}

/**
 * Fractions of a base minor unit [balances] keeps while adding up. Rounding
 * each expense on its own always handed the odd unit to the same member
 * (the first in order), so over many odd amounts the error piled up on one
 * side; summing at this finer scale and rounding once keeps the result
 * within one unit of the exact answer.
 */
private const val PRECISION = 10_000L

/**
 * Net position of every member in base minor units: positive means the
 * others owe them. The values always sum to zero.
 */
fun balances(
    members: List<Member>,
    expenses: List<Expense>,
    settlements: List<Settlement>,
    base: Currency,
): Map<String, Long> {
    val order = members.sortedBy { it.order }.map { it.id }
    val net = LinkedHashMap<String, Long>()
    order.forEach { net[it] = 0 }
    fun credit(id: String, v: Long) { net[id] = add(net[id] ?: 0, v) }

    for (e in expenses) {
        val totalBase = baseTotal(e, base)
        if (totalBase == 0L) continue
        // Weights straight from the expense, not its rounded shares, which
        // would carry the per-expense rounding back in.
        sharesInOriginal(e, order) // Same validation as everywhere else.
        val weights = LinkedHashMap<String, Long>()
        for (id in order) {
            val w = e.splits[id] ?: 0
            if (w > 0) weights[id] = if (e.splitMode == SplitMode.equal) 1 else w
        }
        val shares = allocate(mul(totalBase, PRECISION), weights)
        credit(e.payerId, mul(totalBase, PRECISION))
        shares.forEach { (id, v) -> credit(id, -v) }
    }
    for (s in settlements) {
        credit(s.fromId, mul(s.amountMinor, PRECISION))
        credit(s.toId, -mul(s.amountMinor, PRECISION))
    }
    return roundToUnits(net)
}

/**
 * [scaled] (summing to zero, in 1/[PRECISION] units) rounded to whole units
 * that still sum to zero: everyone is rounded down, then those who lost the
 * most get one unit back, ties in member order.
 */
private fun roundToUnits(scaled: Map<String, Long>): Map<String, Long> {
    val keys = scaled.keys.toList()
    val out = LinkedHashMap<String, Long>()
    val rem = LongArray(keys.size)
    keys.forEachIndexed { i, k ->
        val v = scaled.getValue(k)
        out[k] = v.floorDiv(PRECISION)
        rem[i] = v.mod(PRECISION)
    }
    val missing = -out.values.fold(0L, ::add)
    val byRemainder = keys.indices.sortedWith(compareByDescending<Int> { rem[it] }.thenBy { it })
    for (j in 0 until missing.toInt()) {
        val k = keys[byRemainder[j]]
        out[k] = out.getValue(k) + 1
    }
    return out
}

data class Transfer(val fromId: String, val toId: String, val amountMinor: Long) {
    override fun toString() = "$fromId → $toId: $amountMinor"
}

/**
 * Who should pay whom to bring every balance to zero: the largest debtor
 * repeatedly pays the largest creditor. At most n − 1 transfers; not always
 * the fewest possible (finding that is NP-hard), which doesn't matter for a
 * group of friends.
 */
fun suggestTransfers(net: Map<String, Long>): List<Transfer> {
    val debtors = net.filter { it.value < 0 }.map { it.key to -it.value }.toMutableList()
    val creditors = net.filter { it.value > 0 }.map { it.key to it.value }.toMutableList()
    val out = mutableListOf<Transfer>()
    while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
        // Stable sorts, so equal amounts keep the members' order.
        debtors.sortByDescending { it.second }
        creditors.sortByDescending { it.second }
        val (d, dv) = debtors[0]
        val (c, cv) = creditors[0]
        val amt = minOf(dv, cv)
        out += Transfer(d, c, amt)
        debtors[0] = d to dv - amt
        creditors[0] = c to cv - amt
        debtors.removeAll { it.second == 0L }
        creditors.removeAll { it.second == 0L }
    }
    return out
}
