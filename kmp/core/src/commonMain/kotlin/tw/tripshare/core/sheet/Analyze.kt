package tw.tripshare.core.sheet

/** Everything found in one paste. */
data class ImportResult(
    val expenses: List<ExpenseRow>,
    val items: List<ItemRow>,
    val lodgings: List<LodgingRow>,
    val flights: List<FlightRow>,
    val checklist: List<ChecklistRow>,
    val skipped: List<Skipped>,
) {
    val isEmpty: Boolean
        get() = expenses.isEmpty() && items.isEmpty() && lodgings.isEmpty() && flights.isEmpty() && checklist.isEmpty()

    /** Dates the import uses that fall outside the trip. */
    fun datesOutside(ctx: ImportContext): List<String> =
        (expenses.mapNotNull { it.date } + items.map { it.date } + lodgings.flatMap { listOf(it.checkIn) } + flights.map { it.departDate })
            .filter { it < ctx.tripStart || it > ctx.tripEnd }.distinct().sorted()
}

/**
 * Reads a pasted block: every expense table, days laid out in columns, and
 * row tables of stops, stays, flights and checklist entries. Totals,
 * exchange notes and anything without a recognisable header are left out.
 */
fun analyze(text: String, ctx: ImportContext): ImportResult = analyzeSections(parsePasted(text), ctx)

internal fun analyzeSections(sections: List<Section>, ctx: ImportContext): ImportResult {
    val expenses = mutableListOf<ExpenseRow>()
    val travel = TravelOut()
    for (s in sections) {
        expenses += findExpenses(s, ctx)
        findDayColumns(s, ctx, travel)
        findRowTables(s, ctx, travel)
    }
    return ImportResult(
        expenses = expenses,
        items = travel.items,
        lodgings = travel.lodgings.distinct(),
        flights = travel.flights,
        checklist = travel.checklist.distinctBy { it.kind to it.text },
        skipped = travel.skipped,
    )
}

/** Headers the importer recognises, for the help text. */
const val KNOWN_HEADERS =
    "花費：項目、金額、幣別、折合台幣、支付人、分攤、分類、日期、付款方式、備註\n" +
        "行程：第一列放日期（每天一欄），或用 日期、時間、行程、地點、交通、備註、住宿 欄\n" +
        "住宿：入住、退房、住宿、地址、訂房代碼\n" +
        "航班：航班、出發機場、抵達機場、出發日期、出發時間、抵達日期、抵達時間\n" +
        "清單：清單（行李／出發前／待辦）、項目"

/**
 * The prompt to paste into one's own AI chat together with a sheet, so it
 * answers in tables this importer reads. Nothing is sent anywhere by the app.
 */
fun aiPrompt(ctx: ImportContext, tripName: String): String = """
請把我接下來貼上的旅遊試算表內容，整理成下面幾個 Markdown 表格，讓我匯入旅遊 App「同遊」。

旅程：$tripName，${ctx.tripStart} 到 ${ctx.tripEnd}，結算幣別 ${ctx.base}。
成員：${ctx.memberNames.joinToString("、").ifEmpty { "（尚未建立）" }}

規則：
- 只輸出下面的標題和表格，不要其他說明。沒有資料的段落整段省略。
- 表頭文字要完全照抄，欄位順序也一樣。
- 日期一律寫 yyyy-mm-dd，年份不明時以旅程日期推斷；時間寫 24 小時制 HH:mm。
- 金額只寫數字（不要逗號和符號），幣別寫三碼代碼（TWD、JPY、KRW、EUR…）。
- 試算表若有換算成 ${ctx.base} 的金額，填在「折合${ctx.base}」，沒有就留空，不要自己換算。
- 支付人、分攤寫試算表上的名字；所有人平分就在分攤寫「全部」，只算某些人就用「、」分開。
- 分類只能是：餐飲、交通、住宿、門票、購物、其他。
- 交通只能是：步行、電車、巴士、計程車、開車、飛機、船，看不出來就留空。
- 不要匯入總計、平均、每人應收付、換匯紀錄、代購清單這類彙總資料。
- 看不懂或沒有的資料留空，絕對不要自己編。
- 機場寫三碼代碼（例如 TPE、NRT），航班號碼寫像 BR198 的格式；缺航班號碼或機場的航班改放到行程。
- 住宿的退房日是最後一晚的隔天。

## 花費
| 日期 | 項目 | 金額 | 幣別 | 折合${ctx.base} | 支付人 | 分攤 | 分類 | 備註 |
|---|---|---|---|---|---|---|---|---|

## 行程
| 日期 | 時間 | 行程 | 地點 | 交通 | 備註 |
|---|---|---|---|---|---|

## 住宿
| 入住 | 退房 | 住宿 | 地址 | 訂房代碼 | 備註 |
|---|---|---|---|---|---|

## 航班
| 航班 | 航空公司 | 出發機場 | 抵達機場 | 出發日期 | 出發時間 | 抵達日期 | 抵達時間 | 備註 |
|---|---|---|---|---|---|---|---|---|

## 清單
| 清單 | 項目 |
|---|---|
（清單欄只能是：行李、出發前、待辦）

試算表內容：
""".trimIndent() + "\n"
