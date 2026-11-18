package tw.tripshare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ModelsTest {
    @Test fun ownerIsAbsentWhenNeverSeenOrAfterTwoWeeks() {
        val day = 24L * 3600 * 1000
        val t = Trip("t", "東京", "TWD", "2026-12-20", "2026-12-24", "a", listOf("a"), 0)
        kotlin.test.assertTrue(t.ownerAbsent(100 * day))
        kotlin.test.assertFalse(t.copy(ownerSeenAt = 90 * day).ownerAbsent(100 * day))
        kotlin.test.assertTrue(t.copy(ownerSeenAt = 85 * day).ownerAbsent(100 * day))
    }

    @Test fun currencyFormatsAndParses() {
        val usd = Currency.of("USD")
        assertEquals("123.45", usd.format(12345))
        assertEquals("0.05", usd.format(5))
        assertEquals("-2.50", usd.format(-250))
        assertEquals(12345, usd.parse("123.45"))
        assertEquals(100050, usd.parse("1,000.5"))
        assertNull(usd.parse("1.234"))
        assertNull(usd.parse("abc"))
    }

    @Test fun twdAndJpyCarryNoDecimals() {
        assertEquals(0, Currency.of("TWD").decimals)
        assertEquals(1500, Currency.of("JPY").parse("1500"))
        assertNull(Currency.of("JPY").parse("1500.5"))
    }

    @Test fun unknownCurrencyThrows() {
        assertFailsWith<IllegalArgumentException> { Currency.of("XXX") }
    }

    @Test fun expenseRoundTrips() {
        val e = Expense(
            id = "e1", title = "一蘭拉麵", category = ExpenseCategory.food, amountMinor = 3960, currency = "JPY",
            rateToBase = "0.2134", payerId = "m1", splitMode = SplitMode.shares,
            splits = mapOf("m1" to 2, "m2" to 1), occurredAt = 1766200000000, createdBy = "uid1",
            updatedAt = 1766200000000, note = "宵夜",
        )
        assertEquals(e, Expense.fromMap("e1", e.toMap()))
    }

    @Test fun numbersFromTheWebArriveAsDoubles() {
        // The Firestore JS SDK hands every number back as a Double.
        val m = mapOf<String, Any?>("name" to "阿嬤", "uid" to null, "order" to 1.0, "joinedAt" to 2.0)
        assertEquals(Member("m2", "阿嬤", 1, 2), Member.fromMap("m2", m))
        val e = mapOf<String, Any?>(
            "title" to "t", "category" to "nope", "amountMinor" to 300.0, "currency" to "TWD",
            "rateToBase" to "1", "payerId" to "a", "splitMode" to "equal", "splits" to mapOf("a" to 1.0),
            "occurredAt" to 0.0, "createdBy" to "u", "updatedAt" to 0.0,
        )
        val back = Expense.fromMap("x", e)
        assertEquals(ExpenseCategory.other, back.category)
        assertEquals(mapOf("a" to 1L), back.splits)
    }

    @Test fun tripMemberAndItineraryRoundTrip() {
        val t = Trip("t1", "東京", "TWD", "2026-12-20", "2026-12-25", "uid1", listOf("uid1"), 1)
        assertEquals(t, Trip.fromMap("t1", t.toMap()))
        val mem = Member("m2", "阿嬤", 1, 2)
        assertEquals(mem, Member.fromMap("m2", mem.toMap()))
        val i = ItineraryItem("i1", "2026-12-21", "淺草寺", 0, time = "09:30")
        assertEquals(i, ItineraryItem.fromMap("i1", i.toMap()))
    }

    @Test fun travelModelsRoundTrip() {
        val f = Flight("f1", "BR198", "TPE", "NRT", "2026-12-20", "08:50", airline = "長榮", arriveTime = "13:00")
        assertEquals(f, Flight.fromMap("f1", f.toMap()))
        val l = Lodging("l1", "淺草飯店", "2026-12-20", "2026-12-25", confirmation = "ABC123")
        assertEquals(l, Lodging.fromMap("l1", l.toMap()))
        val c = ChecklistEntry("c1", ChecklistKind.packing, "護照", done = true, order = 3, doneBy = "Alex")
        assertEquals(c, ChecklistEntry.fromMap("c1", c.toMap()))
    }

    @Test fun timelinePutsTimedStopsFirst() {
        val items = listOf(
            ItineraryItem("u2", "d", "untimed 2", 2),
            ItineraryItem("t2", "d", "10:00", 5, time = "10:00"),
            ItineraryItem("u1", "d", "untimed 1", 1),
            ItineraryItem("t1", "d", "09:00", 9, time = "09:00"),
        )
        assertEquals(listOf("t1", "t2", "u1", "u2"), items.sortedWith(ItineraryItem.timelineOrder).map { it.id })
    }
}
