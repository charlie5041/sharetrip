package tw.tripshare.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** One day's expenses on the 記帳 tab. */
data class ExpenseDay(val date: LocalDate, val expenses: List<Expense>)

/**
 * [expenses] by the day they happened, newest day first, and newest first
 * within a day. [dayOf] turns a time into the phone's local date, so a
 * late dinner stays on the day it was eaten. Expenses with the same time
 * (sheet imports put a whole day at noon) keep the order they came in.
 */
fun groupByDay(expenses: List<Expense>, dayOf: (Long) -> LocalDate): List<ExpenseDay> =
    expenses.groupBy { dayOf(it.occurredAt) }
        .map { (date, list) -> ExpenseDay(date, list.sortedByDescending { it.occurredAt }) }
        .sortedByDescending { it.date }

/** 1 for the trip's first day, or null when [date] falls outside the trip. */
fun tripDayNumber(date: LocalDate, startYmd: String, endYmd: String): Int? {
    val start = runCatching { parseYmd(startYmd) }.getOrNull() ?: return null
    val end = runCatching { parseYmd(endYmd) }.getOrNull() ?: return null
    if (date < start || date > end) return null
    return start.daysUntil(date) + 1
}
