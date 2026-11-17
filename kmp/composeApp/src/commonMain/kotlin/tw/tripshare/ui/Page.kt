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
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AppColors.paper).safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f)) {
                TopBar(title, onBack, actions)
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                bottom?.invoke()
            }
        }
        ToastHost(Modifier.align(Alignment.BottomCenter))
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
