package tw.tripshare.core.sheet

import tw.tripshare.core.ChecklistKind
import tw.tripshare.core.ExpenseCategory
import tw.tripshare.core.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Layouts modelled on real travel sheets; every name, place, date and amount here is made up. */
class SheetImportTest {
    private val ctx = ImportContext("2030-03-04", "2030-03-11", "TWD", listOf("Alex", "Bella"))
    private val members = listOf("m1" to "Alex", "m2" to "Bella")
    private fun choices(names: Map<String, String> = autoMatch(listOf("alex", "bella"), members)) =
        ExpenseChoices("TWD", null, names, mapOf("JPY" to "0.21"), listOf("m1", "m2"))

    private fun tsv(vararg rows: String) = rows.joinToString("\n")

    @Test fun readsMoneyAsWritten() {
        assertEquals(Money("2350", "TWD"), parseMoney("NT$2,350"))
        assertEquals(Money("98000", "KRW"), parseMoney("₩98,000"))
        assertEquals(Money("24.50", "EUR"), parseMoney("€24.50"))
        assertEquals(Money("180.00", "HUF"), parseMoney(" Ft180.00"))
        assertEquals(Money("15.00", "CZK"), parseMoney("15.00 Kč"))
        assertEquals(Money("800", "CZK"), parseMoney("800克朗"))
        assertEquals(Money("4500", null), parseMoney("4,500"))
        assertEquals(Money("700", "JPY"), parseMoney("¥700"))
        assertEquals("金額是一個範圍", runCatching { parseMoney("NT$800-1200") }.exceptionOrNull()?.message)
        assertEquals("負數金額不能匯入", runCatching { parseMoney("-NT$450") }.exceptionOrNull()?.message)
        assertEquals("一格裡有好幾個數字", runCatching { parseMoney("NT5000+NT2000") }.exceptionOrNull()?.message)
    }

    @Test fun readsDatesNearTheTrip() {
        assertEquals("2030-03-04", parseSheetDate("3/4 (Mon)", "2030-03-04"))
        assertEquals("2025-04-30", parseSheetDate("2025/04/30 (三)", "2030-03-04"))
        assertEquals("2027-01-05", parseSheetDate("1/5 (Tue)", "2026-12-30"))
        assertEquals("2026-06-01", parseSheetDate("6/1 Day1", "2026-06-01"))
        assertNull(parseSheetDate("16:35 ~ 18:35", "2030-03-04"))
        assertNull(parseSheetDate("機場到飯店", "2030-03-04"))
    }

    @Test fun splitsTimes() {
        assertEquals(Timed("08:10", null, "桃園 - 成田"), splitTime("08:10桃園 - 成田"))
        assertEquals(Timed("02:50", "06:10", "XX793"), splitTime("02:50 - 06:10 XX793"))
        assertEquals(Timed("01:25", null, "(+1)"), splitTime("01:25(+1)"))
        assertEquals(Timed(null, null, "20min"), splitTime("20min"))
    }

    /** The "模板" layout: days across, 住宿/行程 labels down, the expense table to the right. */
    @Test fun templateLayout() {
        val text = tsv(
            "日期\t3/4 (Mon)\t3/5 (Tue)\t3/6 (Wed)\t\t項目\t金額\t幣別\t付款方式\t支出人\t備註\t\t",
            "住宿 🛌\t港區\t港區\t朋友家\t\t小貝機票\t8,800\t台幣\t\tBella\t\tAlex\tNT\$20,000",
            "\t(Trip)港區範例飯店\t(Trip)港區範例飯店\t\t\t阿力機票\t\t\t\t\t\tBella\tNT\$18,000",
            "\t標準雙床房 無早\t標準雙床房 無早\t\t\t湖畔民宿 A\t3,500\t台幣\t其他信用卡\tBella\tBK ¥16,000\t平均一人\tNT\$19,000",
            "行程 📋\t08:10桃園 - 12:30成田 ✈️\t横浜赤レンガ倉庫\t\"NEX成田特快\n(東京車站-成田)\"\t\t拉麵\t¥1,200\t\t現金\talex\t",
            "\t\t横浜中華街\t\t\t\t\t\t\t\t",
        )
        val r = analyze(text, ctx)

        assertEquals(listOf("小貝機票", "阿力機票", "湖畔民宿 A", "拉麵"), r.expenses.map { it.title })
        val first = r.expenses[0]
        assertEquals("TWD", first.namedCurrency)
        assertEquals("Bella", first.payer)
        assertEquals(null, r.expenses[1].problem)
        assertEquals(null, r.expenses[1].money)
        assertEquals(ExpenseCategory.lodging, r.expenses[2].category)
        assertEquals("其他信用卡｜BK ¥16,000", r.expenses[2].note)
        assertEquals("JPY", r.expenses[3].namedCurrency)
        assertEquals(ExpenseCategory.food, r.expenses[3].category)

        assertEquals(
            listOf("2030-03-04 08:10 桃園 - 12:30成田 ✈️", "2030-03-05 null 横浜赤レンガ倉庫", "2030-03-06 null NEX成田特快", "2030-03-05 null 横浜中華街"),
            r.items.map { "${it.date} ${it.time} ${it.title}" },
        )
        assertEquals(Transport.flight, r.items[0].transport)
        assertEquals("(東京車站-成田)", r.items[2].note)
        assertEquals(Transport.train, r.items[2].transport)

        assertEquals(1, r.lodgings.size)
        val stay = r.lodgings.single()
        assertEquals("(Trip)港區範例飯店", stay.name)
        assertEquals("2030-03-04" to "2030-03-06", stay.checkIn to stay.checkOut)
        assertEquals("港區 · 標準雙床房 無早", stay.note)
    }

    @Test fun settlesExpenses() {
        val text = tsv(
            "項目\t金額\t幣別\t台幣\t付費方式\t支付人\t備註",
            "市區住宿\t200.00\t歐元\t6880\t刷卡\tAlex\t",
            "火車票\t4,500\t台幣\t\t刷卡\t\t各自刷卡",
            "拉麵\t¥1,200\t\t\t現金\t阿力\t",
        )
        val rows = analyze(text, ctx.copy(tripStart = "2031-05-04", tripEnd = "2031-05-19")).expenses
        val c = choices()
        val hotel = rows[0].settle(ctx, c) { 0L } as Settled.Ready
        assertEquals(20000L, hotel.expense.amountMinor)
        assertEquals("EUR", hotel.expense.currency)
        assertEquals("34.4", hotel.expense.rateToBase)
        assertEquals(RateSource.sheet, hotel.rateSource)
        assertEquals(mapOf("m1" to 1L, "m2" to 1L), hotel.expense.splits)

        assertEquals(Settled.Problem("沒有填支付人"), rows[1].settle(ctx, c) { 0L })
        val withDefault = rows[1].settle(ctx, c.copy(defaultPayerId = "m2")) { 0L } as Settled.Ready
        assertEquals("m2", withDefault.expense.payerId)
        assertEquals("1", withDefault.expense.rateToBase)

        assertEquals(Settled.Problem("「阿力」還沒對應到成員"), rows[2].settle(ctx, c) { 0L })
        val mapped = rows[2].settle(ctx, c.copy(names = c.names + (nameKey("阿力") to "m1"))) { 0L } as Settled.Ready
        assertEquals("0.21", mapped.expense.rateToBase)
        assertEquals(RateSource.chosen, mapped.rateSource)
        assertEquals(listOf("Alex", "阿力"), mentionedNames(rows).let { n -> n.filter { it != "Bella" } })
    }

    @Test fun seoulLayoutPrefersConvertedColumnAfterTheItem() {
        val text = tsv(
            "在韓換匯\t\t花費項目",
            "台幣\t韓幣\t項目\t金額\t付費方式\t折合台幣\t備註\t支付人\t\tShoes\t₩120,000",
            "既有韓幣\t₩50,000\t機場範例酒店\t₩98,000\t刷卡\tNT\$2,350\t手續費 NT\$30\tBella\t\t折合台幣\tNT\$3,000",
            "NT\$8,000\t₩330,000\t機場計程車\t₩15,000\t現金\t\t\t\t\t飯店費用\tNT\$9,000",
        )
        val rows = analyze(text, ctx).expenses
        assertEquals(listOf("機場範例酒店", "機場計程車"), rows.map { it.title })
        assertEquals("2350", rows[0].baseNumber)
        assertNull(rows[1].baseNumber)
        assertEquals("KRW", rows[1].namedCurrency)
    }

    /** Days in pairs of columns (time, place), as in the 2025 sheets. */
    @Test fun dayPairsLayout() {
        val text = tsv(
            "2031/07/10 (四)\t\t2031/07/11 (五)\t\t\t花費",
            "7:30\t桃園機場T1\t8:30 取車\tBudget Car Rental 🚗\t\t項目",
            "10:45\t熊本阿蘇空港\t\t阿蘇火山火口\t\t",
            "20min\t🚌 機場巴士\t\t\t\t",
        )
        val r = analyze(text, ctx.copy(tripStart = "2031-07-10", tripEnd = "2031-07-14"))
        assertEquals(
            listOf("2031-07-10 07:30 桃園機場T1", "2031-07-11 08:30 Budget Car Rental 🚗", "2031-07-10 10:45 熊本阿蘇空港", "2031-07-11 null 阿蘇火山火口", "2031-07-10 null 🚌 機場巴士"),
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
            "5/5 (一)\t湖區小鎮\t10:00 ~ 16:20\t✈️ 上海 - 維也納\t\tNT\$0\t\tFerienwohnung",
            "\t\t17:00 ~ 17:20\t🚃 OBB 機場 - 維也納\t€5.00\tNT\$170\t\t",
            "5/6 (二)\t\t\t湖邊散步\t\t\t\t",
            "5/7 (三)\t維也納\t\t美景宮\t\t\t\tHotel Wien",
        )
        val r = analyze(text, ctx.copy(tripStart = "2031-05-04", tripEnd = "2031-05-19"))
        assertEquals(listOf("2031-05-05", "2031-05-05", "2031-05-06", "2031-05-07"), r.items.map { it.date })
        assertEquals("10:00", r.items[0].time)
        assertEquals("10:00–16:20", r.items[0].note)
        assertEquals(Transport.train, r.items[1].transport)
        assertTrue(r.expenses.isEmpty())
        assertEquals(
            listOf("Ferienwohnung 2031-05-05 2031-05-07", "Hotel Wien 2031-05-07 2031-05-08"),
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
            | 2030-03-05 | 拉麵 | 1200 | JPY |  | Alex | 全部 | 餐飲 |  |
            | 2030-03-06 | Bella 外套 | 3000 | JPY | 650 | Alex | Bella | 購物 | 代購 |

            ## 行程
            | 日期 | 時間 | 行程 | 地點 | 交通 | 備註 |
            |---|---|---|---|---|---|
            | 2030-03-05 | 10:00 | 橫濱紅磚倉庫 | 横浜赤レンガ倉庫 | 電車 |  |

            ## 住宿
            | 入住 | 退房 | 住宿 | 地址 | 訂房代碼 | 備註 |
            |---|---|---|---|---|---|
            | 2030-03-04 | 2030-03-06 | 港區範例飯店 |  | 12345 |  |

            ## 航班
            | 航班 | 航空公司 | 出發機場 | 抵達機場 | 出發日期 | 出發時間 | 抵達日期 | 抵達時間 | 備註 |
            |---|---|---|---|---|---|---|---|---|
            | XX800 | 星宇 | TPE | NRT | 2030-03-04 | 08:10 | 2030-03-04 | 12:30 |  |
            | 華航 | 華航 | 桃園 | 成田 | 2030-03-11 | 19:40 |  |  |  |

            ## 清單
            | 清單 | 項目 |
            |---|---|
            | 行李 | 護照 |
            | 出發前 | 換日幣 |
        """.trimIndent()
        val r = analyze(text, ctx)
        assertEquals(2, r.expenses.size)
        assertEquals("2030-03-06", r.expenses[1].date)
        assertEquals(listOf("Bella"), r.expenses[1].splitNames)
        assertNull(r.expenses[0].splitNames)
        assertEquals(ExpenseCategory.shopping, r.expenses[1].category)
        val coat = r.expenses[1].settle(ctx, choices()) { 0L } as Settled.Ready
        assertEquals(mapOf("m2" to 1L), coat.expense.splits)
        assertEquals("0.216667", coat.expense.rateToBase)

        assertEquals(Transport.train, r.items.single().transport)
        assertEquals("横浜赤レンガ倉庫", r.items.single().place)
        assertEquals("12345", r.lodgings.single().confirmation)
        assertEquals("XX800", r.flights.single().flightNo)
        assertEquals("12:30", r.flights.single().arriveTime)
        assertEquals("沒有航班號碼", r.skipped.single().reason)
        assertEquals(listOf(ChecklistKind.packing to "護照", ChecklistKind.predeparture to "換日幣"), r.checklist.map { it.kind to it.text })
    }

    @Test fun promptNamesTheTrip() {
        val p = aiPrompt(ctx, "範例小旅行")
        assertTrue("範例小旅行" in p && "折合TWD" in p && "Alex、Bella" in p)
    }

    @Test fun nothingRecognisable() {
        assertTrue(analyze("隨便一段文字\n沒有表頭", ctx).isEmpty)
    }
}
