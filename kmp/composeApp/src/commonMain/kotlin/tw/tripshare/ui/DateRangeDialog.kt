package tw.tripshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import tw.tripshare.core.shortDate
import tw.tripshare.core.ymd

/**
 * Pick a trip's first and last day on a month calendar: tap the start,
 * then the end. Built here rather than pulling in Material's picker, which
 * would add to the web download.
 */
@Composable
fun DateRangeDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    today: LocalDate,
    onDone: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    single: Boolean = false,
    startLabel: String = "出發日",
    endLabel: String = "回程日",
) {
    var month by remember { mutableStateOf(LocalDate((initialStart ?: today).year, (initialStart ?: today).month, 1)) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }

    val hint = when {
        single -> start?.let { shortDate(ymd(it)) } ?: "點一天"
        start == null -> "點$startLabel"
        end == null -> "${shortDate(ymd(start!!))} 開始，再點$endLabel"
        else -> "${shortDate(ymd(start!!))} – ${shortDate(ymd(end!!))}"
    }
    InkDialog("選擇日期", onDismiss, buttons = {
        TextAction("取消", onDismiss)
        InkButton("確定", { onDone(start!!, end ?: start!!) }, enabled = start != null)
    }) {
        Txt(hint, size = 14, color = AppColors.faint)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundAction("‹", { month = month.minus(DatePeriod(months = 1)) })
            Txt("${month.year} 年 ${month.month.ordinal + 1} 月", Modifier.weight(1f), bold = true, align = androidx.compose.ui.text.style.TextAlign.Center)
            RoundAction("›", { month = month.plus(DatePeriod(months = 1)) })
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Txt(it, Modifier.weight(1f), size = 13, color = AppColors.faint, align = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val lead = month.dayOfWeek.ordinal // Monday = 0
        val next = month.plus(DatePeriod(months = 1))
        val days = generateSequence(month) { it.plus(DatePeriod(days = 1)) }.takeWhile { it < next }.toList()
        val cells: List<LocalDate?> = List(lead) { null } + days
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        val d = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (d != null) {
                                val s = start
                                val e = end
                                val edge = d == s || d == e
                                val inside = s != null && e != null && d > s && d < e
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(1f)
                                        .then(if (edge) Modifier.border(AppShape.line, AppColors.coral, CircleShape) else Modifier)
                                        .background(
                                            when {
                                                edge -> AppColors.coral
                                                inside -> AppColors.yamabuki
                                                else -> AppColors.paper
                                            },
                                            CircleShape,
                                        )
                                        .clickable {
                                            when {
                                                single -> { start = d; end = d }
                                                s == null || e != null || d < s -> { start = d; end = null }
                                                else -> end = d
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Txt("${d.day}", size = 15, bold = d == today)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
