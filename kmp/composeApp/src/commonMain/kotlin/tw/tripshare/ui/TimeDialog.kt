package tw.tripshare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tw.tripshare.core.hm

/**
 * Pick a time of day by tapping: an hour, then a minute in fives, with
 * ‹ › for the odd minute. Built here, like [DateRangeDialog], rather than
 * pulling in Material's picker.
 */
@Composable
fun TimeDialog(hour: Int, minute: Int, onDone: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var h by remember { mutableIntStateOf(hour.coerceIn(0, 23)) }
    var m by remember { mutableIntStateOf(minute.coerceIn(0, 59)) }
    InkDialog("選擇時間", onDismiss, buttons = {
        TextAction("取消", onDismiss)
        InkButton("確定", { onDone(h, m) })
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundAction("‹", { val t = (h * 60 + m + 1439) % 1440; h = t / 60; m = t % 60 })
            Txt(hm(h, m), Modifier.weight(1f), size = 34, bold = true, align = TextAlign.Center)
            RoundAction("›", { val t = (h * 60 + m + 1) % 1440; h = t / 60; m = t % 60 })
        }
        Txt("時", size = 14, color = AppColors.faint)
        Grid((0..23).toList(), 4, { it == h }, { it.toString() }) { h = it }
        Txt("分", size = 14, color = AppColors.faint)
        Grid((0..55 step 5).toList(), 4, { it == m }, { it.toString().padStart(2, '0') }) { m = it }
    }
}

@Composable
private fun Grid(values: List<Int>, columns: Int, selected: (Int) -> Boolean, label: (Int) -> String, onPick: (Int) -> Unit) {
    values.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { v -> Chip(label(v), selected(v), { onPick(v) }, Modifier.weight(1f)) }
        }
    }
}
