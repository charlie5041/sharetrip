package tw.tripshare.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** "2026-12-20" → a date; trips store dates as these strings. */
fun parseYmd(s: String): LocalDate = LocalDate.parse(s)

fun ymd(d: LocalDate): String = d.toString()

private val WEEKDAYS = mapOf(
    DayOfWeek.MONDAY to "一", DayOfWeek.TUESDAY to "二", DayOfWeek.WEDNESDAY to "三",
    DayOfWeek.THURSDAY to "四", DayOfWeek.FRIDAY to "五", DayOfWeek.SATURDAY to "六", DayOfWeek.SUNDAY to "日",
)

fun weekdayLabel(d: LocalDate): String = WEEKDAYS.getValue(d.dayOfWeek)

/** "12/20（六）" */
fun shortDate(ymdText: String): String {
    val d = parseYmd(ymdText)
    return "${d.month.ordinal + 1}/${d.day}（${weekdayLabel(d)}）"
}

/** Whole days from [today] until [startYmd]; negative once it has begun. */
fun daysUntil(startYmd: String, today: LocalDate): Int = today.daysUntil(parseYmd(startYmd))

/** Every date from [start] to [end], both included. */
fun datesBetween(start: String, end: String): List<String> {
    val s = parseYmd(start)
    val n = s.daysUntil(parseYmd(end))
    return (0..maxOf(n, 0)).map { ymd(s.plus(DatePeriod(days = it))) }
}

/** The badge on a trip card. */
fun tripBadge(trip: Trip, today: LocalDate): String {
    val days = daysUntil(trip.startDate, today)
    return when {
        days > 0 -> "還有 $days 天"
        daysUntil(trip.endDate, today) >= 0 -> "旅行中"
        else -> "已結束"
    }
}
