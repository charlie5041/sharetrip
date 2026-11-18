package tw.tripshare.core.sheet

import tw.tripshare.core.ChecklistKind
import tw.tripshare.core.ExpenseCategory
import tw.tripshare.core.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Layouts taken from a real travel sheet (names and amounts as in it). */
class SheetImportTest {
    private val ctx = ImportContext("2026-11-04", "2026-11-11", "TWD", listOf("Charlie", "Sandy"))
    private val members = listOf("m1" to "Charlie", "m2" to "Sandy")
    private fun choices(names: Map<String, String> = autoMatch(listOf("charlie", "sandy"), members)) =
        ExpenseChoices("TWD", null, names, mapOf("JPY" to "0.21"), listOf("m1", "m2"))

    private fun tsv(vararg rows: String) = rows.joinToString("\n")

    @Test fun readsMoneyAsWritten() {
        assertEquals(Money("2617", "TWD"), parseMoney("NT$2,617"))
        assertEquals(Money("108912", "KRW"), parseMoney("₩108,912"))
        assertEquals(Money("27.90", "EUR"), parseMoney("€27.90"))
        assertEquals(Money("210.00", "HUF"), parseMoney(" Ft210.00"))
        assertEquals(Money("20.00", "CZK"), parseMoney("20.00 Kč"))
        assertEquals(Money("1000", "CZK"), parseMoney("1000克朗"))
        assertEquals(Money("4886", null), parseMoney("4,886"))
        assertEquals(Money("600", "JPY"), parseMoney("¥600"))
        assertEquals("金額是一個範圍", runCatching { parseMoney("NT$1000-1500") }.exceptionOrNull()?.message)
        assertEquals("負數金額不能匯入", runCatching { parseMoney("-NT$603") }.exceptionOrNull()?.message)
        assertEquals("一格裡有好幾個數字", runCatching { parseMoney("NT6555+NT3211") }.exceptionOrNull()?.message)
    }

    @Test fun readsDatesNearTheTrip() {
        assertEquals("2026-11-04", parseSheetDate("11/4 (Wed)", "2026-11-04"))
        assertEquals("2025-04-30", parseSheetDate("2025/04/30 (三)", "2026-11-04"))
        assertEquals("2027-01-05", parseSheetDate("1/5 (Tue)", "2026-12-30"))
        assertEquals("2026-06-01", parseSheetDate("6/1 Day1", "2026-06-01"))
        assertNull(parseSheetDate("16:35 ~ 18:35", "2026-11-04"))
        assertNull(parseSheetDate("機場到飯店", "2026-11-04"))
    }

    @Test fun splitsTimes() {
        assertEquals(Timed("17:15", null, "桃園 - 成田"), splitTime("17:15桃園 - 成田"))
        assertEquals(Timed("02:50", "06:10", "BX793"), splitTime("02:50 - 06:10 BX793"))
        assertEquals(Timed("01:25", null, "(+1)"), splitTime("01:25(+1)"))
        assertEquals(Timed(null, null, "20min"), splitTime("20min"))
    }

    /** The "模板" layout: days across, 住宿/行程 labels down, the expense table to the right. */
    @Test fun templateLayout() {
        val text = tsv(
            "日期\t11/4 (Wed)\t11/5 (Thu)\t11/6 (Fri)\t\t項目\t金額\t幣別\t付款方式\t支出人\t備註\t\t",
            "住宿 🛌\t東品川\t東品川\t溫暖的家\t\t珊迪機票\t9,691\t台幣\t\tSandy\t\tCharlie\tNT\$31,917",
            "\t(Trip)東品川哈頓飯店\t(Trip)東品川哈頓飯店\t\t\t查理機票\t\t\t\t\t\tSandy\tNT\$30,711",
            "\t經濟雙床房 無早\t經濟雙床房 無早\t\t\t吉田市 Noborisaka\t3,878\t台幣\t其他信用卡\tSandy\tBK ¥18,636\t平均一人\tNT\$31,314",
            "行程 📋\t17:15桃園 - 21:35成田 ✈️\t横浜赤レンガ倉庫\t\"NEX成田特快\n(東京車站-成田)\"\t\t拉麵\t¥1,200\t\t現金\tcharlie\t",
            "\t\t横浜中華街\t\t\t\t\t\t\t\t",
        )
        val r = analyze(text, ctx)

        assertEquals(listOf("珊迪機票", "查理機票", "吉田市 Noborisaka", "拉麵"), r.expenses.map { it.title })
        val first = r.expenses[0]
        assertEquals("TWD", first.namedCurrency)
        assertEquals("Sandy", first.payer)
        assertEquals(null, r.expenses[1].problem)
        assertEquals(null, r.expenses[1].money)
        assertEquals(ExpenseCategory.lodging, r.expenses[2].category)
        assertEquals("其他信用卡｜BK ¥18,636", r.expenses[2].note)
        assertEquals("JPY", r.expenses[3].namedCurrency)
        assertEquals(ExpenseCategory.food, r.expenses[3].category)

        assertEquals(
            listOf("2026-11-04 17:15 桃園 - 21:35成田 ✈️", "2026-11-05 null 横浜赤レンガ倉庫", "2026-11-06 null NEX成田特快", "2026-11-05 null 横浜中華街"),
            r.items.map { "${it.date} ${it.time} ${it.title}" },
        )
        assertEquals(Transport.flight, r.items[0].transport)
        assertEquals("(東京車站-成田)", r.items[2].note)
        assertEquals(Transport.train, r.items[2].transport)

        assertEquals(1, r.lodgings.size)
        val stay = r.lodgings.single()
        assertEquals("(Trip)東品川哈頓飯店", stay.name)
        assertEquals("2026-11-04" to "2026-11-06", stay.checkIn to stay.checkOut)
        assertEquals("東品川 · 經濟雙床房 無早", stay.note)
    }

    @Test fun settlesExpenses() {
        val text = tsv(
            "項目\t金額\t幣別\t台幣\t付費方式\t支付人\t備註",
            "布達佩斯住宿\t212.32\t歐元\t7311\t刷卡\tCharlie\t",
            "火車票\t4,886\t台幣\t\t刷卡\t\t各自刷卡",
            "拉麵\t¥1,200\t\t\t現金\t查理\t",
        )
        val rows = analyze(text, ctx.copy(tripStart = "2024-10-04", tripEnd = "2024-10-19")).expenses
        val c = choices()
        val hotel = rows[0].settle(ctx, c) { 0L } as Settled.Ready
        assertEquals(21232L, hotel.expense.amountMinor)
        assertEquals("EUR", hotel.expense.currency)
        assertEquals("34.4339", hotel.expense.rateToBase)
        assertEquals(RateSource.sheet, hotel.rateSource)
        assertEquals(mapOf("m1" to 1L, "m2" to 1L), hotel.expense.splits)

        assertEquals(Settled.Problem("沒有填支付人"), rows[1].settle(ctx, c) { 0L })
        val withDefault = rows[1].settle(ctx, c.copy(defaultPayerId = "m2")) { 0L } as Settled.Ready
        assertEquals("m2", withDefault.expense.payerId)
        assertEquals("1", withDefault.expense.rateToBase)

        assertEquals(Settled.Problem("「查理」還沒對應到成員"), rows[2].settle(ctx, c) { 0L })
        val mapped = rows[2].settle(ctx, c.copy(names = c.names + (nameKey("查理") to "m1"))) { 0L } as Settled.Ready
        assertEquals("0.21", mapped.expense.rateToBase)
        assertEquals(RateSource.chosen, mapped.rateSource)
        assertEquals(listOf("Charlie", "查理"), mentionedNames(rows).let { n -> n.filter { it != "Sandy" } })
    }

    @Test fun seoulLayoutPrefersConvertedColumnAfterTheItem() {
        val text = tsv(
            "在韓換匯\t\t花費項目",
            "台幣\t韓幣\t項目\t金額\t付費方式\t折合台幣\t備註\t支付人\t\tKeds\t₩150,000",
            "既有韓幣\t₩71,000\t仁川戴斯酒店\t₩108,912\t刷卡\tNT\$2,617\t手續費 NT\$39\tSandy\t\t折合台幣\tNT\$3,750",
            "NT\$10,000\t₩414,000\t機場計程車\t₩17,100\t現金\t\t\t\t\t飯店費用\tNT\$12,544",
        )
        val rows = analyze(text, ctx).expenses
        assertEquals(listOf("仁川戴斯酒店", "機場計程車"), rows.map { it.title })
        assertEquals("2617", rows[0].baseNumber)
        assertNull(rows[1].baseNumber)
        assertEquals("KRW", rows[1].namedCurrency)
    }

    /** Days in pairs of columns (time, place), as in the 2025 sheets. */
    @Test fun dayPairsLayout() {
        val text = tsv(
            "2025/08/14 (四)\t\t2025/08/15 (五)\t\t\t花費",
            "7:30\t桃園機場T1\t8:30 取車\tBudget Car Rental 🚗\t\t項目",
            "10:45\t熊本阿蘇空港\t\t阿蘇火山火口\t\t",
            "20min\t🚌 機場巴士\t\t\t\t",
        )
        val r = analyze(text, ctx.copy(tripStart = "2025-08-14", tripEnd = "2025-08-18"))
        assertEquals(
            listOf("2025-08-14 07:30 桃園機場T1", "2025-08-15 08:30 Budget Car Rental 🚗", "2025-08-14 10:45 熊本阿蘇空港", "2025-08-15 null 阿蘇火山火口", "2025-08-14 null 🚌 機場巴士"),
            r.items.map { "${it.date} ${it.time} ${it.title}" },
        )
        assertEquals("取車", r.items[1].note)
        assertEquals(Transport.car, r.items[1].transport)
        assertEquals("20min", r.items[4].note)
        assertEquals(Transport.bus, r.items[4].transport)
    }

    /** One stop per row with merged dates, and a 住宿 column. */
    @Test fun rowTableWithMergedDates() {
        val text = tsv(
            "日期\t城市\t時間\t備註\t費用\t折合台幣\t餐廳\t住宿",
            "10/5 (六)\t上特勞恩\t09:30 ~ 15:55\t✈️ 上海 - 維也納\t\tNT\$0\t\tFerienwohnung",
            "\t\t16:33 ~ 16:48\t🚃 OBB 機場 - 維也納\t€4.50\tNT\$158\t\t",
            "10/6 (日)\t\t\t湖邊散步\t\t\t\t",
            "10/7 (一)\t維也納\t\t美景宮\t\t\t\tHotel Wien",
        )
        val r = analyze(text, ctx.copy(tripStart = "2024-10-04", tripEnd = "2024-10-19"))
        assertEquals(listOf("2024-10-05", "2024-10-05", "2024-10-06", "2024-10-07"), r.items.map { it.date })
        assertEquals("09:30", r.items[0].time)
        assertEquals("09:30–15:55", r.items[0].note)
        assertEquals(Transport.train, r.items[1].transport)
        assertTrue(r.expenses.isEmpty())
        assertEquals(
            listOf("Ferienwohnung 2024-10-05 2024-10-07", "Hotel Wien 2024-10-07 2024-10-08"),
            r.lodgings.map { "${it.name} ${it.checkIn} ${it.checkOut}" },
        )
    }

    /** What an AI chat sends back for the prompt. */
    @Test fun aiMarkdownAnswer() {
        val text = """
            好的，以下是整理結果：
            ## 花費
            | 日期 | 項目 | 金額 | 幣別 | 折合TWD | 支付人 | 分攤 | 分類 | 備註 |
            |---|---|---|---|---|---|---|---|---|
            | 2026-11-05 | 拉麵 | 1200 | JPY |  | Charlie | 全部 | 餐飲 |  |
            | 2026-11-06 | Sandy 外套 | 3245 | JPY | 700 | Charlie | Sandy | 購物 | 代購 |

            ## 行程
            | 日期 | 時間 | 行程 | 地點 | 交通 | 備註 |
            |---|---|---|---|---|---|
            | 2026-11-05 | 10:00 | 橫濱紅磚倉庫 | 横浜赤レンガ倉庫 | 電車 |  |

            ## 住宿
            | 入住 | 退房 | 住宿 | 地址 | 訂房代碼 | 備註 |
            |---|---|---|---|---|---|
            | 2026-11-04 | 2026-11-06 | 東品川哈頓飯店 |  | 12345 |  |

            ## 航班
            | 航班 | 航空公司 | 出發機場 | 抵達機場 | 出發日期 | 出發時間 | 抵達日期 | 抵達時間 | 備註 |
            |---|---|---|---|---|---|---|---|---|
            | JX800 | 星宇 | TPE | NRT | 2026-11-04 | 17:15 | 2026-11-04 | 21:35 |  |
            | 華航 | 華航 | 桃園 | 成田 | 2026-11-11 | 20:55 |  |  |  |

            ## 清單
            | 清單 | 項目 |
            |---|---|
            | 行李 | 護照 |
            | 出發前 | 換日幣 |
        """.trimIndent()
        val r = analyze(text, ctx)
        assertEquals(2, r.expenses.size)
        assertEquals("2026-11-06", r.expenses[1].date)
        assertEquals(listOf("Sandy"), r.expenses[1].splitNames)
        assertNull(r.expenses[0].splitNames)
        assertEquals(ExpenseCategory.shopping, r.expenses[1].category)
        val coat = r.expenses[1].settle(ctx, choices()) { 0L } as Settled.Ready
        assertEquals(mapOf("m2" to 1L), coat.expense.splits)
        assertEquals("0.215716", coat.expense.rateToBase)

        assertEquals(Transport.train, r.items.single().transport)
        assertEquals("横浜赤レンガ倉庫", r.items.single().place)
        assertEquals("12345", r.lodgings.single().confirmation)
        assertEquals("JX800", r.flights.single().flightNo)
        assertEquals("21:35", r.flights.single().arriveTime)
        assertEquals("沒有航班號碼", r.skipped.single().reason)
        assertEquals(listOf(ChecklistKind.packing to "護照", ChecklistKind.predeparture to "換日幣"), r.checklist.map { it.kind to it.text })
    }

    @Test fun promptNamesTheTrip() {
        val p = aiPrompt(ctx, "富士山重機遊")
        assertTrue("富士山重機遊" in p && "折合TWD" in p && "Charlie、Sandy" in p)
    }

    @Test fun nothingRecognisable() {
        assertTrue(analyze("隨便一段文字\n沒有表頭", ctx).isEmpty)
    }
}
