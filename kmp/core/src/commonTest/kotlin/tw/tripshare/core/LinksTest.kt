package tw.tripshare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinksTest {
    @Test fun searchesByPlace() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=%E6%B7%BA%E8%8D%89%E5%AF%BA%20A%26B", mapLink("淺草寺 A&B", null))
    }

    @Test fun prefersSavedLinkButOnlyHttp() {
        assertEquals("https://maps.app.goo.gl/x", mapLink("x", " https://maps.app.goo.gl/x "))
        assertNull(mapLink("x", "javascript:alert(1)"))
        assertNull(mapLink(" ", null))
    }
}
