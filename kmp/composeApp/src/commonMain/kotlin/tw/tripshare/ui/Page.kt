package tw.tripshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The top bar: an optional back button, the title, then actions. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) RoundAction("←", onBack)
        Txt(title, Modifier.weight(1f).padding(start = 4.dp), size = 22, bold = true, maxLines = 1)
        actions()
    }
}

/**
 * A whole screen: top bar, then [content] scrolling in a column no wider
 * than a phone, so it also reads well on a computer.
 */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
    scroll: Boolean = true,
    fab: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AppColors.paper).paperDots().safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f)) {
                TopBar(title, onBack, actions)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        Modifier.fillMaxSize()
                            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            // Room for the cards' shadows, and for the floating button.
                            .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = if (fab != null) 96.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content,
                    )
                    if (fab != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { fab() }
                }
                bottom?.invoke()
            }
        }
        ToastHost(Modifier.align(Alignment.BottomCenter))
    }
}

/** The scrapbook's dotted notebook paper; nothing in the minimal look. */
private fun Modifier.paperDots(): Modifier {
    if (!Looks.scrapbook) return this
    val dot = AppColors.line
    return drawBehind {
        val step = 16.dp.toPx()
        val points = ArrayList<Offset>()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) { points.add(Offset(x, y)); x += step }
            y += step
        }
        drawPoints(points, PointMode.Points, dot, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun ToastHost(modifier: Modifier) {
    val toasts = LocalToasts.current
    val msg = toasts.message ?: return
    LaunchedEffect(msg) {
        delay(3500)
        toasts.clear()
    }
    Box(modifier.padding(16.dp).widthIn(max = 560.dp)) {
        InkCard(fill = AppColors.yamabuki, onClick = toasts::clear) { Txt(msg) }
    }
}
