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
