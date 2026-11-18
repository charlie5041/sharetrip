package tw.tripshare.core.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayFlightTest {
    private val ctx = ImportContext("2026-11-04", "2026-11-11", "TWD", listOf("Charlie"))

    @Test fun dayCellsWithAFlightBecomeFlights() {
        val text = "2026/11/4\t\t2026/11/5\t\n17:15\t桃園 - 21:30成田 MM628\t9:00\t河口湖\n20:55\t桃園 - 00:50(+1)羽田 MM860\t\t\n"
        val r = analyze(text, ctx)
        assertEquals(
            listOf(
                FlightRow("MM628", "TPE", "NRT", "2026-11-04", "17:15", arriveDate = "2026-11-04", arriveTime = "21:30"),
                FlightRow("MM860", "TPE", "HND", "2026-11-04", "20:55", arriveDate = "2026-11-05", arriveTime = "00:50"),
            ),
            r.flights,
        )
        assertEquals(listOf("河口湖"), r.items.map { it.title })
    }

    @Test fun oneAirportStaysAStop() {
        assertNull(dayFlight(ItemRow("2025-10-27", "14:05", "BKK 素萬那普機場")))
    }
}

class PendingAmountTest {
    private val ctx = ImportContext("2026-11-04", "2026-11-11", "TWD", listOf("Charlie", "Sandy"))
    private val choices = ExpenseChoices(
        defaultCurrency = "JPY", defaultPayerId = "c", names = mapOf("charlie" to "c", "sandy" to "s"),
        rates = mapOf("JPY" to "0.21"), memberOrder = listOf("c", "s"),
    )

    @Test fun rowsWithoutAnAmountComeInAsZeroAndSettleToNothing() {
        val r = analyze("項目\t金額\t支付人\n全家雨傘\t\tCharlie\n宵夜\t0\tSandy\n拉麵\t¥1,200\tCharlie\n", ctx)
        val settled = r.expenses.map { it.settle(ctx, choices) { 0L } }
        val ready = settled.map { it as tw.tripshare.core.sheet.Settled.Ready }
        assertEquals(listOf(true, true, false), ready.map { it.pending })
        assertEquals(listOf(0L, 0L, 1200L), ready.map { it.expense.amountMinor })
        val members = listOf("c", "s").map { tw.tripshare.core.Member(id = it, name = it, order = 0, joinedAt = 0) }
        val zeroOnly = tw.tripshare.core.balances(members, ready.take(2).map { it.expense.copy(id = "x") }, emptyList(), tw.tripshare.core.Currency.of("TWD"))
        assertEquals(true, zeroOnly.values.all { it == 0L })
    }
}

class CategoryTest {
    @Test fun whatWasBoughtBeatsTheShop() {
        assertEquals(tw.tripshare.core.ExpenseCategory.shopping, guessCategory("Lawson * Coleman 外套"))
        assertEquals(tw.tripshare.core.ExpenseCategory.shopping, guessCategory("Big Camera 麵包超人跟泡澡球"))
        assertEquals(tw.tripshare.core.ExpenseCategory.food, guessCategory("Lawson"))
        assertEquals(tw.tripshare.core.ExpenseCategory.food, guessCategory("Saison Bakery&Cafe"))
    }
}
