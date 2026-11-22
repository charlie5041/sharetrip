package tw.tripshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ROW = 44.dp
private const val ROWS = 5 // Odd, so one row sits in the middle.
private const val LOOPS = 200 // Rounds of the wheel; far more than anyone scrolls.

/**
 * Pick a time of day on two wheels, hours and minutes, like the phone's own
 * clock: scroll until the number sits in the band, or tap a number to bring
 * it there. Built here, like [DateRangeDialog], rather than pulling in
 * Material's picker.
 */
@Composable
fun TimeDialog(hour: Int, minute: Int, onDone: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var h by remember { mutableIntStateOf(hour.coerceIn(0, 23)) }
    var m by remember { mutableIntStateOf(minute.coerceIn(0, 59)) }
    InkDialog("選擇時間", onDismiss, buttons = {
        TextAction("取消", onDismiss)
        InkButton("確定", { onDone(h, m) })
    }) {
        Box(Modifier.fillMaxWidth().height(ROW * ROWS), contentAlignment = Alignment.Center) {
            // The band the picked numbers sit in.
            Box(Modifier.fillMaxWidth().height(ROW).clip(AppShape.small).background(AppColors.yamabuki, AppShape.small))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Wheel(24, h) { h = it }
                Txt(":", size = 26, bold = true)
                Wheel(60, m) { m = it }
            }
        }
    }
}

/** A looping column of 0 until [count], two digits each, reporting the one in the middle. */
@Composable
private fun Wheel(count: Int, initial: Int, onChange: (Int) -> Unit) {
    val start = count * (LOOPS / 2) + initial
    val state = rememberLazyListState(start)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val scope = rememberCoroutineScope()
    val center by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: start
        }
    }
    LaunchedEffect(center) { onChange(center % count) }
    LazyColumn(
        Modifier.width(72.dp).height(ROW * ROWS),
        state = state,
        // Padding of two rows each side, so the first and last numbers can reach the middle.
        contentPadding = PaddingValues(vertical = ROW * (ROWS / 2)),
        flingBehavior = fling,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count * LOOPS) { i ->
            val near = abs(i - center)
            Box(
                Modifier.fillMaxWidth().height(ROW).clickable { scope.launch { state.animateScrollToItem(i) } },
                contentAlignment = Alignment.Center,
            ) {
                Txt(
                    (i % count).toString().padStart(2, '0'),
                    size = if (near == 0) 26 else 20,
                    bold = near == 0,
                    color = if (near == 0) AppColors.ink else AppColors.faint,
                )
            }
        }
    }
}
