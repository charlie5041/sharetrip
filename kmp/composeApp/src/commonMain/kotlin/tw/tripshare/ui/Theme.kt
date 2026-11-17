package tw.tripshare.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tw.tripshare.res.Res
import tw.tripshare.res.huninn

/**
 * The picture-book look, same as the Flutter app's theme.dart: washi-white
 * paper, thick ink outlines, big rounded corners, flat fills, light only.
 */
object AppColors {
    val paper = Color(0xFFFFFCF6) // 和紙白
    val ink = Color(0xFF3E3A39) // 墨灰
    val coral = Color(0xFFF2A497) // 珊瑚粉
    val yamabuki = Color(0xFFF6D27A) // 山吹黃
    val mizu = Color(0xFFA8D3E6) // 水色
    val wakatake = Color(0xFFB9DAA5) // 若竹綠
    val error = Color(0xFFD9534F)
    val faint = Color(0x993E3A39)
    val white = Color(0xFFFFFFFF)
}

object AppShape {
    val border = 3.dp
    val radius = RoundedCornerShape(20.dp)
    val small = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(999.dp)
}

val LocalTextStyle = staticCompositionLocalOf { TextStyle.Default }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val style = TextStyle(fontFamily = FontFamily(Font(Res.font.huninn)), color = AppColors.ink, fontSize = 16.sp)
    CompositionLocalProvider(LocalTextStyle provides style, content = content)
}
