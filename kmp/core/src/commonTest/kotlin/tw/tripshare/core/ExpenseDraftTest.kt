package tw.tripshare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ExpenseDraftTest {
    private val order = listOf("a", "b", "c")
    private fun draft() = ExpenseDraft(
        title = " 拉麵 ", category = ExpenseCategory.food, amount = "3000", currency = "JPY", base = "TWD",
        rate = "0.2134", payerId = "a", mode = SplitMode.equal, included = setOf("c", "a"),
        values = emptyMap(), occurredAt = 5, note = " ",
    )

    @Test fun buildsEqualSplitInMemberOrder() {
        val e = draft().build(order)
        assertEquals("拉麵", e.title)
        assertEquals(3000L, e.amountMinor)
        assertEquals(listOf("a" to 1L, "c" to 1L), e.splits.toList())
        assertNull(e.note)
    }

    @Test fun baseCurrencyIgnoresTypedRate() {
        assertEquals("1", draft().copy(currency = "TWD", rate = "abc").build(order).rateToBase)
    }

    @Test fun exactMustAddUp() {
        val d = draft().copy(mode = SplitMode.exact, values = mapOf("a" to "1000", "c" to "1500"))
        assertFailsWith<SplitError> { d.build(order) }
        assertEquals(2000L, d.copy(values = mapOf("a" to "1000", "c" to "2000")).build(order).splits["c"])
    }

    @Test fun rejectsBadInput() {
        assertFailsWith<SplitError> { draft().copy(title = " ").build(order) }
        assertFailsWith<SplitError> { draft().copy(amount = "0").build(order) }
        assertFailsWith<SplitError> { draft().copy(rate = "").build(order) }
        assertFailsWith<SplitError> { draft().copy(included = emptySet()).build(order) }
        assertFailsWith<SplitError> { draft().copy(mode = SplitMode.shares, values = mapOf("a" to "0", "c" to "1")).build(order) }
    }

    @Test fun roundTripsThroughEditing() {
        val e = draft().copy(currency = "USD", amount = "12.5", mode = SplitMode.exact, values = mapOf("a" to "10", "c" to "2.50")).build(order)
        val again = ExpenseDraft.of(e, "TWD").build(order)
        assertEquals(e, again)
        assertEquals("2.50", ExpenseDraft.of(e, "TWD").values["c"])
    }

    @Test fun formatsRatesToSixDigits() {
        assertEquals("0.213456", formatRate(0.2134561))
        assertEquals("31.2", formatRate(31.2))
        assertEquals("1", formatRate(1.0))
        assertEquals("1234570", formatRate(1234567.0))
        assertEquals("10", formatRate(9.9999999))
        assertEquals("0.0000152", formatRate(1.52e-5))
        assertNull(formatRate(0.0))
        assertNull(formatRate(Double.NaN))
    }

    @Test fun parsesTimes() {
        assertEquals(18 to 30, parseHm("18:30"))
        assertEquals(8 to 5, parseHm("8：05"))
        assertNull(parseHm("24:00"))
        assertEquals("08:05", hm(8, 5))
    }
}
