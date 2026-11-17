package tw.tripshare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.Font
import tw.tripshare.res.Res
import tw.tripshare.res.huninn

/** The picture-book palette, same as the Flutter app's theme.dart. */
object AppColors {
    val paper = Color(0xFFFFFCF6) // 和紙白
    val ink = Color(0xFF3E3A39) // 墨灰
    val coral = Color(0xFFF2A497) // 珊瑚粉
    val yamabuki = Color(0xFFF6D27A) // 山吹黃
    val mizu = Color(0xFFA8D3E6) // 水色
    val wakatake = Color(0xFFB9DAA5) // 若竹綠
}

object AppShape {
    val border = 3.dp
    val radius = RoundedCornerShape(20.dp)
    val small = RoundedCornerShape(14.dp)
}

@Composable
fun huninn(): FontFamily = FontFamily(Font(Res.font.huninn))

/** A flat card with the thick ink outline. */
fun Modifier.inkCard(fill: Color = AppColors.paper): Modifier = this
    .border(AppShape.border, AppColors.ink, AppShape.radius)
    .background(fill, AppShape.radius)
    .padding(16.dp)
