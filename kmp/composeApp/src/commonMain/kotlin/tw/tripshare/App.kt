package tw.tripshare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.flowOf

val LocalTextStyle = staticCompositionLocalOf { TextStyle.Default }

@Composable
fun Txt(text: String, size: Int = 16, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = LocalTextStyle.current.copy(fontSize = size.sp))
}

@Composable
fun App() {
    val style = TextStyle(fontFamily = huninn(), color = AppColors.ink, fontSize = 16.sp)
    CompositionLocalProvider(LocalTextStyle provides style) {
        Column(
            Modifier.fillMaxSize().background(AppColors.paper).safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Txt("同遊 · Kotlin 原型", size = 26)
            Txt("平台：$platformName", size = 14)
            TypingCard()
            FirebaseCard()
        }
    }
}

@Composable
private fun TypingCard() {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().inkCard(AppColors.yamabuki), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("① 試打注音／日文", size = 18)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
            cursorBrush = SolidColor(AppColors.ink),
            modifier = Modifier.fillMaxWidth()
                .border(AppShape.border, AppColors.ink, AppShape.small)
                .background(AppColors.paper, AppShape.small)
                .padding(12.dp),
        )
        Txt("你打的字：$text（${text.length} 字）", size = 14)
    }
}

@Composable
private fun FirebaseCard() {
    var uid by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Backend.start(); Backend.signIn() }
            .onSuccess { uid = it }
            .onFailure { error = it.toString() }
    }
    val trips by remember(uid) { uid?.let(Backend::myTrips) ?: flowOf(null) }
        .collectAsState(null)
    Column(Modifier.fillMaxWidth().inkCard(AppColors.mizu), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("② Firebase 連線", size = 18)
        when {
            error != null -> Txt("失敗：$error", size = 14)
            uid == null -> Txt("登入中…", size = 14)
            else -> {
                Txt("匿名登入成功：${uid!!.take(8)}…", size = 14)
                val list = trips
                if (list == null) Txt("讀取旅程中…", size = 14)
                else if (list.isEmpty()) Txt("這個帳號還沒有旅程（新帳號是正常的）", size = 14)
                else list.forEach { Txt("・${it.name}　${it.startDate}", size = 14) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
