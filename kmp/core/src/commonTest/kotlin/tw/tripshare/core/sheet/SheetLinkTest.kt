package tw.tripshare.core.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetLinkTest {
    private val id = "1ExampleSheetIdForTestsOnly_abcdefghijklmnop"

    @Test fun readsLinks() {
        assertEquals(SheetRef(id, null), parseSheetLink("https://docs.google.com/spreadsheets/d/$id/edit?usp=drivesdk"))
        assertEquals(SheetRef(id, 123456L), parseSheetLink(" https://docs.google.com/spreadsheets/u/0/d/$id/edit#gid=123456 "))
        assertEquals(SheetRef(id, 0L), parseSheetLink("https://docs.google.com/spreadsheets/d/$id/edit?gid=0#gid=0"))
        assertNull(parseSheetLink("https://example.com/spreadsheets/d/$id"))
        assertNull(parseSheetLink("隨便打字"))
    }

    @Test fun buildsUrls() {
        assertTrue(valuesUrl(id, "20300304-0311 範例小旅行", "K").contains("/values/%2720300304-0311%20%E7%AF%84"))
        assertTrue(valuesUrl(id, "Alex's", "K").contains("%27Alex%27%27s%27"))
    }

    @Test fun readsTabs() {
        val json = """
            {"properties":{"title":"旅伴出國玩"},
             "sheets":[{"properties":{"sheetId":0,"title":"20300101~20300105範例A"}},
                       {"properties":{"sheetId":98765,"title":"模板","hidden":true}},
                       {"properties":{"sheetId":42,"title":"海邊小鎮"}}]}
        """
        val info = parseTabs(json)
        assertEquals("旅伴出國玩", info.title)
        assertEquals(listOf(SheetTab("20300101~20300105範例A", 0), SheetTab("海邊小鎮", 42)), info.tabs)
    }

    @Test fun readsValuesAndFeedsTheImporter() {
        val json = """
            {"range":"'x'!A1:Z100","majorDimension":"ROWS","values":[
              ["項目","金額","幣別","付費方式","支付人","備註"],
              ["市區住宿 \"官網\"","€200.00","歐元","刷卡","Alex","line1\nline2 é"],
              [],
              ["火車票","4,500"]
            ]}
        """
        val rows = parseValues(json)
        assertEquals(4, rows.size)
        assertEquals("市區住宿 \"官網\"", rows[1][0])
        assertEquals("line1\nline2 é", rows[1][5])
        val r = analyzeRows(rows, ImportContext("2031-05-04", "2031-05-19", "TWD", listOf("Alex")))
        assertEquals(listOf("市區住宿 \"官網\"", "火車票"), r.expenses.map { it.title })
        assertEquals("EUR", r.expenses[0].namedCurrency)
    }

    @Test fun explainsRefusals() {
        val disabled = """{"error":{"code":403,"message":"Google Sheets API has not been used in project 123456789012 before or it is disabled.","status":"PERMISSION_DENIED"}}"""
        assertTrue(explainSheetError(403, disabled).contains("尚未開啟"))
        val private = """{"error":{"code":403,"message":"The caller does not have permission","status":"PERMISSION_DENIED"}}"""
        assertTrue(explainSheetError(403, private).contains("知道連結的任何人"))
        assertTrue(explainSheetError(404, "").contains("找不到"))
    }
}
