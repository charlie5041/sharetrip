package tw.tripshare.core

enum class SplitMode { equal, exact, shares }

enum class ExpenseCategory(val label: String) {
    food("餐飲"), transport("交通"), lodging("住宿"), ticket("門票"), shopping("購物"), other("其他"),

    /** Written by 麻將計點 when a game ends; not offered in the expense form. */
    mahjong("麻將"),
}

/** One payment by one member, split among some members. */
data class Expense(
    val id: String,
    val title: String,
    val category: ExpenseCategory,
    /** In [currency]'s minor units. */
    val amountMinor: Long,
    val currency: String,
    /**
     * One unit of [currency] in the trip's base currency, as a decimal
     * string ("0.2134"), fixed when the expense was recorded. A string, not
     * a double, so settling never inherits binary rounding error.
     */
    val rateToBase: String,
    val payerId: String,
    val splitMode: SplitMode,
    /**
     * memberId → weight. equal: 1 per participant; exact: that member's
     * amount in [currency] minor units; shares: number of shares.
     */
    val splits: Map<String, Long>,
    val occurredAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val note: String? = null,
    /** False keeps it out of 總花費 (e.g. mahjong winnings); balances count it either way. */
    val inTotal: Boolean = true,
    /** The 麻將計點 game this expense settles, if any. */
    val mahjongId: String? = null,
) {
    fun toMap(): Doc = mapOf(
        "title" to title,
        "category" to category.name,
        "amountMinor" to amountMinor,
        "currency" to currency,
        "rateToBase" to rateToBase,
        "payerId" to payerId,
        "splitMode" to splitMode.name,
        "splits" to splits,
        "occurredAt" to occurredAt,
        "note" to note,
        "createdBy" to createdBy,
        "updatedAt" to updatedAt,
    ) + (if (inTotal) emptyMap() else mapOf("inTotal" to false)) +
        (mahjongId?.let { mapOf("mahjongId" to it) } ?: emptyMap())

    companion object {
        fun fromMap(id: String, m: Doc) = Expense(
            id = id,
            title = m.str("title"),
            category = ExpenseCategory.entries.firstOrNull { it.name == m["category"] } ?: ExpenseCategory.other,
            amountMinor = m.long("amountMinor"),
            currency = m.str("currency"),
            rateToBase = m.str("rateToBase"),
            payerId = m.str("payerId"),
            splitMode = SplitMode.valueOf(m.str("splitMode")),
            splits = m.longMap("splits"),
            occurredAt = m.long("occurredAt"),
            note = m.strOrNull("note"),
            createdBy = m.str("createdBy"),
            updatedAt = m.long("updatedAt"),
            inTotal = m.boolOr("inTotal", true),
            mahjongId = m.strOrNull("mahjongId"),
        )
    }
}

/** A repayment someone marked as done, in the trip's base currency. */
data class Settlement(
    val id: String,
    val fromId: String,
    val toId: String,
    val amountMinor: Long,
    val at: Long,
    val createdBy: String,
) {
    fun toMap(): Doc = mapOf(
        "fromId" to fromId, "toId" to toId, "amountMinor" to amountMinor, "at" to at, "createdBy" to createdBy,
    )

    companion object {
        fun fromMap(id: String, m: Doc) = Settlement(
            id = id,
            fromId = m.str("fromId"),
            toId = m.str("toId"),
            amountMinor = m.long("amountMinor"),
            at = m.long("at"),
            createdBy = m.str("createdBy"),
        )
    }
}
