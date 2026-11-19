package tw.tripshare.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpenseDaysTest {
    private val hour = 3600L * 1000
    private val day = 24 * hour

    // Times count hours from 2030-01-01 00:00 in a made-up zone, so the test needs no time zone.
    private fun dayOf(millis: Long) = LocalDate(2030, 1, 1 + (millis / day).toInt())

    private fun exp(id: String, at: Long) = Expense(
        id = id, title = "測試$id", category = ExpenseCategory.food, amountMinor = 100, currency = "TWD",
        rateToBase = "1", payerId = "a", splitMode = SplitMode.equal, splits = mapOf("a" to 1L),
        occurredAt = at, createdBy = "u", updatedAt = 0,
    )

    @Test fun groupsNewestDayFirstAndNewestFirstWithinADay() {
        val days = groupByDay(
            listOf(exp("1", 9 * hour), exp("2", day + 8 * hour), exp("3", 20 * hour), exp("4", day + 23 * hour)),
            ::dayOf,
        )
        assertEquals(listOf(LocalDate(2030, 1, 2), LocalDate(2030, 1, 1)), days.map { it.date })
        assertEquals(listOf("4", "2"), days[0].expenses.map { it.id })
        assertEquals(listOf("3", "1"), days[1].expenses.map { it.id })
    }

    @Test fun sameTimeKeepsIncomingOrder() {
        // Sheet imports put a whole day at noon.
        val noon = 12 * hour
        val days = groupByDay(listOf(exp("a", noon), exp("b", noon), exp("c", noon)), ::dayOf)
        assertEquals(listOf("a", "b", "c"), days.single().expenses.map { it.id })
    }

    @Test fun emptyListHasNoDays() {
        assertEquals(emptyList(), groupByDay(emptyList(), ::dayOf))
    }

    @Test fun tripDayCountsFromOneAndIsNullOutside() {
        assertEquals(1, tripDayNumber(LocalDate(2030, 3, 10), "2030-03-10", "2030-03-14"))
        assertEquals(5, tripDayNumber(LocalDate(2030, 3, 14), "2030-03-10", "2030-03-14"))
        assertNull(tripDayNumber(LocalDate(2030, 3, 9), "2030-03-10", "2030-03-14"))
        assertNull(tripDayNumber(LocalDate(2030, 3, 15), "2030-03-10", "2030-03-14"))
        assertNull(tripDayNumber(LocalDate(2030, 3, 10), "", ""))
    }
}
