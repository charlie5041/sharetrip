package tw.tripshare.core.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayFlightTest {
    private val ctx = ImportContext("2030-03-04", "2030-03-11", "TWD", listOf("Alex"))

    @Test fun dayCellsWithAFlightBecomeFlights() {
        val text = "2030/3/4\t\t2030/3/5\t\n08:10\t桃園 - 12:20成田 XX628\t9:00\t河口湖\n19:40\t桃園 - 01:05(+1)羽田 XX860\t\t\n"
        val r = analyze(text, ctx)
        assertEquals(
            listOf(
                FlightRow("XX628", "TPE", "NRT", "2030-03-04", "08:10", arriveDate = "2030-03-04", arriveTime = "12:20"),
                FlightRow("XX860", "TPE", "HND", "2030-03-04", "19:40", arriveDate = "2030-03-05", arriveTime = "01:05"),
            ),
            r.flights,
        )
        assertEquals(listOf("河口湖"), r.items.map { it.title })
    }

    @Test fun oneAirportStaysAStop() {
        assertNull(dayFlight(ItemRow("2031-10-27", "14:05", "BKK 素萬那普機場")))
    }
}

class PendingAmountTest {
    private val ctx = ImportContext("2030-03-04", "2030-03-11", "TWD", listOf("Alex", "Bella"))
    private val choices = ExpenseChoices(
        defaultCurrency = "JPY", defaultPayerId = "c", names = mapOf("alex" to "c", "bella" to "s"),
        rates = mapOf("JPY" to "0.21"), memberOrder = listOf("c", "s"),
    )

    @Test fun rowsWithoutAnAmountComeInAsZeroAndSettleToNothing() {
        val r = analyze("項目\t金額\t支付人\n便利商店雨傘\t\tAlex\n宵夜\t0\tBella\n拉麵\t¥1,200\tAlex\n", ctx)
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
        assertEquals(tw.tripshare.core.ExpenseCategory.shopping, guessCategory("Lawson * 戶外品牌 外套"))
        assertEquals(tw.tripshare.core.ExpenseCategory.shopping, guessCategory("Big Camera 玩具跟泡澡球"))
        assertEquals(tw.tripshare.core.ExpenseCategory.food, guessCategory("Lawson"))
        assertEquals(tw.tripshare.core.ExpenseCategory.food, guessCategory("Sample Bakery&Cafe"))
    }
}
