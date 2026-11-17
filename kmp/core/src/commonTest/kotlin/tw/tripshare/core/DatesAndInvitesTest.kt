package tw.tripshare.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatesAndInvitesTest {
    private val trip = Trip("t", "東京", "TWD", "2026-12-20", "2026-12-25", "u", listOf("u"), 0)

    @Test fun shortDateShowsMonthDayAndWeekday() {
        assertEquals("12/20（日）", shortDate("2026-12-20"))
        assertEquals("1/3（六）", shortDate("2026-01-03"))
    }

    @Test fun badgeCountsDownThenShowsTravelling() {
        assertEquals("還有 3 天", tripBadge(trip, LocalDate(2026, 12, 17)))
        assertEquals("旅行中", tripBadge(trip, LocalDate(2026, 12, 20)))
        assertEquals("旅行中", tripBadge(trip, LocalDate(2026, 12, 25)))
        assertEquals("已結束", tripBadge(trip, LocalDate(2026, 12, 26)))
    }

    @Test fun datesBetweenIncludesBothEnds() {
        assertEquals(listOf("2026-12-31", "2027-01-01", "2027-01-02"), datesBetween("2026-12-31", "2027-01-02"))
        assertEquals(listOf("2026-12-20"), datesBetween("2026-12-20", "2026-12-20"))
    }

    @Test fun inviteCodesUseOnlyTheUnambiguousAlphabet() {
        val code = InviteCodes.fromBytes(ByteArray(8) { (it * 37).toByte() })
        assertEquals(8, code.length)
        assertTrue(code.all { it in InviteCodes.ALPHABET })
        assertEquals("ABCD-EFGH", InviteCodes.format("ABCDEFGH"))
        assertEquals("ABCDEFGH", InviteCodes.normalize(" abcd-efgh "))
    }
}
