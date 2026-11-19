package tw.tripshare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from the Flutter app's test/split_test.dart, with the same cases,
// so both apps settle a trip to the same numbers.
class SplitTest {
    private val twd = Currency.of("TWD")
    private val jpy = Currency.of("JPY")
    private val usd = Currency.of("USD")

    private val members = listOf(
        Member("a", "A", 0, 0),
        Member("b", "B", 1, 0),
        Member("c", "C", 2, 0),
    )
    private val order = listOf("a", "b", "c")

    private fun exp(
        amount: Long = 300,
        currency: String = "TWD",
        rate: String = "1",
        payer: String = "a",
        mode: SplitMode = SplitMode.equal,
        splits: Map<String, Long> = mapOf("a" to 1, "b" to 1, "c" to 1),
    ) = Expense(
        id = "x", title = "t", category = ExpenseCategory.food, amountMinor = amount, currency = currency,
        rateToBase = rate, payerId = payer, splitMode = mode, splits = splits, occurredAt = 0,
        createdBy = "u", updatedAt = 0,
    )

    private fun m(vararg p: Pair<String, Long>) = linkedMapOf(*p)

    @Test fun allocateTiesGoInOrder() {
        assertEquals(m("a" to 34, "b" to 33, "c" to 33), allocate(100, m("a" to 1, "b" to 1, "c" to 1)))
        val r = allocate(101, m("c" to 1, "a" to 1, "b" to 1))
        assertEquals(m("c" to 34, "a" to 34, "b" to 33), r)
        assertEquals(listOf("c", "a", "b"), r.keys.toList())
    }

    @Test fun allocateByShares() {
        assertEquals(m("a" to 200, "b" to 100), allocate(300, m("a" to 2, "b" to 1)))
        assertEquals(m("a" to 67, "b" to 33), allocate(100, m("a" to 2, "b" to 1)))
        // 16.67 / 66.67 / 16.67: equal remainders, so the first two in order.
        assertEquals(m("a" to 17, "b" to 67, "c" to 16), allocate(100, m("a" to 1, "b" to 4, "c" to 1)))
    }

    @Test fun allocateAlwaysSumsToTheTotal() {
        for (t in 0L until 500L step 7) {
            assertEquals(t, allocate(t, m("a" to 3, "b" to 5, "c" to 7)).values.sum())
        }
    }

    @Test fun allocateRejectsNobody() {
        assertFailsWith<SplitError> { allocate(100, m("a" to 0)) }
    }

    @Test fun equalIgnoresWeightsAndFollowsMemberOrder() {
        val e = exp(amount = 100, splits = mapOf("c" to 5, "a" to 1))
        assertEquals(m("a" to 50, "c" to 50), sharesInOriginal(e, order))
    }

    @Test fun exactMustAddUp() {
        val ok = exp(mode = SplitMode.exact, splits = mapOf("a" to 100, "b" to 200))
        assertEquals(m("a" to 100, "b" to 200), sharesInOriginal(ok, order))
        val bad = exp(mode = SplitMode.exact, splits = mapOf("a" to 100, "b" to 100))
        assertFailsWith<SplitError> { sharesInOriginal(bad, order) }
    }

    @Test fun jpyToTwdRoundsHalfUp() {
        // 3960 × 0.2134 = 845.064
        assertEquals(845, convert(3960, jpy, twd, Rate.parse("0.2134")))
        // 1000 × 0.2135 = 213.5
        assertEquals(214, convert(1000, jpy, twd, Rate.parse("0.2135")))
    }

    @Test fun convertHandlesDecimalPlaces() {
        // USD 12.34 × 32.5 = 401.05 TWD
        assertEquals(401, convert(1234, usd, twd, Rate.parse("32.5")))
        // TWD 1000 × 0.0308 = USD 30.80
        assertEquals(3080, convert(1000, twd, usd, Rate.parse("0.0308")))
    }

    @Test fun rejectsMalformedRates() {
        assertNull(Rate.tryParse("abc"))
        assertNull(Rate.tryParse("0"))
        assertNull(Rate.tryParse("-1"))
        assertNotNull(Rate.tryParse("1.5"))
        assertNotNull(Rate.tryParse("0.00004321"))
    }

    @Test fun hugeAmountsFailInsteadOfWrapping() {
        val e = exp(amount = 900_000_000_000_000, currency = "VND", rate = "1234.5678")
        assertFailsWith<SplitError> { baseTotal(e, twd) }
    }

    @Test fun foreignExpenseCreditsPayerExactlyWhatOthersOwe() {
        val e = exp(amount = 1000, currency = "JPY", rate = "0.2134")
        val net = balances(members, listOf(e), emptyList(), twd)
        // 1000 JPY splits 334/333/333; 213 TWD then splits 71/71/71.
        assertEquals(m("a" to 142, "b" to -71, "c" to -71), net)
        assertEquals(0, net.values.sum())
    }

    @Test fun baseCurrencyExpenseIgnoresItsStoredRate() {
        val net = balances(members, listOf(exp(amount = 300, rate = "999")), emptyList(), twd)
        assertEquals(m("a" to 200, "b" to -100, "c" to -100), net)
    }

    @Test fun settlementsMoveBalancesTowardZero() {
        val s = Settlement("s", fromId = "b", toId = "a", amountMinor = 100, at = 0, createdBy = "u")
        val net = balances(members, listOf(exp(amount = 300)), listOf(s), twd)
        assertEquals(m("a" to 100, "b" to 0, "c" to -100), net)
    }

    @Test fun mixedCurrenciesSumToZero() {
        val expenses = listOf(
            exp(amount = 3960, currency = "JPY", rate = "0.2134", payer = "b"),
            exp(
                amount = 1234, currency = "USD", rate = "32.5", payer = "c",
                mode = SplitMode.shares, splits = mapOf("a" to 2, "b" to 1, "c" to 1),
            ),
            exp(amount = 777, payer = "a", splits = mapOf("b" to 1, "c" to 1)),
        )
        assertEquals(0, balances(members, expenses, emptyList(), twd).values.sum())
    }

    @Test fun oddAmountsDoNotDriftTowardTheFirstMember() {
        // Two people, every amount odd: the halves used to round the same way
        // each time, so B ended up owing 0.5 less per expense.
        val two = members.take(2)
        val both = mapOf("a" to 1L, "b" to 1L)
        val expenses = List(10) { exp(amount = 101, payer = "a", splits = both) } +
            List(10) { exp(amount = 99, payer = "b", splits = both) }
        // Exactly: B owes 10 × 50.5 − 10 × 49.5 = 10.
        assertEquals(m("a" to 10, "b" to -10), balances(two, expenses, emptyList(), twd))
    }

    @Test fun roundingOnceStillSumsToZero() {
        val net = balances(members, List(7) { exp(amount = 100, payer = "a") }, emptyList(), twd)
        assertEquals(0, net.values.sum())
        // Exactly a: +466.67, b and c: −233.33 each; each lands within one unit.
        assertEquals(m("a" to 466, "b" to -233, "c" to -233), net)
    }

    @Test fun transfersSettleWithAtMostNMinusOne() {
        val t = suggestTransfers(m("a" to 500, "b" to -200, "c" to -300))
        assertEquals(listOf(Transfer("c", "a", 300), Transfer("b", "a", 200)), t)
    }

    @Test fun nothingToDoWhenEven() {
        assertTrue(suggestTransfers(m("a" to 0, "b" to 0)).isEmpty())
    }

    @Test fun applyingTransfersZeroesEveryBalance() {
        val net = m("a" to 120, "b" to -45, "c" to 80, "d" to -155, "e" to 0)
        val after = net.toMutableMap()
        val t = suggestTransfers(net)
        for (x in t) {
            after[x.fromId] = after.getValue(x.fromId) + x.amountMinor
            after[x.toId] = after.getValue(x.toId) - x.amountMinor
        }
        assertTrue(after.values.all { it == 0L })
        assertTrue(t.size <= net.size - 1)
    }
}
