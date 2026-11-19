package tw.tripshare.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tw.tripshare.loadFlag
import tw.tripshare.res.Res
import tw.tripshare.res.huninn
import tw.tripshare.saveFlag

/** The two looks people can pick in 外觀. Minimal is the default. */
enum class Look(val label: String, val hint: String) {
    minimal("極簡", "白卡片、大數字，清爽好讀"),
    scrapbook("手帳", "紙張、票根和便條，像在寫旅行日記"),
}

/** The look in use; reading it from a composable redraws it on change. */
object Looks {
    private const val KEY = "look-scrapbook"
    // Read on first use, which is the first frame: the platform is set up by then.
    var current by mutableStateOf(if (loadFlag(KEY)) Look.scrapbook else Look.minimal)
        private set

    fun set(look: Look) {
        current = look
        saveFlag(KEY, look == Look.scrapbook)
    }

    val scrapbook: Boolean get() = current == Look.scrapbook
}

/**
 * One look's colors. The names come from the first, picture-book design and
 * now stand for roles: [coral] the main action, [yamabuki] a highlight,
 * [mizu] information, [wakatake] done / joined.
 */
class Palette(
    val paper: Color, // page background
    val card: Color, // cards and fields
    val ink: Color, // text
    val faint: Color, // secondary text
    val line: Color, // outlines and dividers
    val coral: Color,
    val yamabuki: Color,
    val mizu: Color,
    val wakatake: Color,
    val good: Color, // money owed to someone
    val error: Color,
    val shadow: Color, // the scrapbook's flat paper shadow
)

private val MINIMAL = Palette(
    paper = Color(0xFFF8F9FA),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF111827),
    faint = Color(0xFF6B7280),
    line = Color(0xFFE5E7EB),
    coral = Color(0xFF0066FF), // 海洋藍
    yamabuki = Color(0xFFEAF2FF),
    mizu = Color(0xFFE0F2FE),
    wakatake = Color(0xFFD1FAE5),
    good = Color(0xFF00A86B),
    error = Color(0xFFE5484D),
    shadow = Color(0x14000000),
)

private val SCRAPBOOK = Palette(
    paper = Color(0xFFFAF6F0), // 奶油紙
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF3B2F2A),
    faint = Color(0xFF8C7B6B),
    line = Color(0xFFE0D4C2),
    coral = Color(0xFFFF7A59), // 暖陽橘
    yamabuki = Color(0xFFFBD46D), // 芥末黃
    mizu = Color(0xFFDCEBFF),
    wakatake = Color(0xFFA8D5A2),
    good = Color(0xFF2E7D32), // 森林綠
    error = Color(0xFFD9534F),
    shadow = Color(0xFFE6DCCB),
)

private fun palette(): Palette = if (Looks.scrapbook) SCRAPBOOK else MINIMAL

object AppColors {
    val paper: Color get() = palette().paper
    val card: Color get() = palette().card
    val ink: Color get() = palette().ink
    val faint: Color get() = palette().faint
    val line: Color get() = palette().line
    val coral: Color get() = palette().coral
    val yamabuki: Color get() = palette().yamabuki
    val mizu: Color get() = palette().mizu
    val wakatake: Color get() = palette().wakatake
    val good: Color get() = palette().good
    val error: Color get() = palette().error
    val shadow: Color get() = palette().shadow
    val white: Color get() = palette().card
}

/** Text that reads on [fill]: white on dark fills, ink otherwise. */
fun onColor(fill: Color): Color = if (fill.luminance() < 0.3f) Color.White else AppColors.ink

object AppShape {
    /** Outline width of fields and outlined buttons. */
    val border: Dp get() = if (Looks.scrapbook) 2.dp else 1.5.dp
    /** Outline width of small things: avatars, tick boxes, tags. */
    val line: Dp get() = if (Looks.scrapbook) 2.dp else 1.5.dp
    val radius: RoundedCornerShape get() = if (Looks.scrapbook) RoundedCornerShape(10.dp) else RoundedCornerShape(20.dp)
    val small: RoundedCornerShape get() = if (Looks.scrapbook) RoundedCornerShape(8.dp) else RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(999.dp)
    val tiny = RoundedCornerShape(6.dp)
}

val LocalTextStyle = staticCompositionLocalOf { TextStyle.Default }

/** The color [Txt] uses when none is given; cards on dark fills switch it to white. */
val LocalContentColor = compositionLocalOf<Color?> { null }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val style = TextStyle(fontFamily = FontFamily(Font(Res.font.huninn)), color = AppColors.ink, fontSize = 16.sp)
    CompositionLocalProvider(LocalTextStyle provides style, content = content)
}

/**
 * An emoji to put before (or after) a label: only in the scrapbook look,
 * where it reads as a sticker. The minimal look stays text only.
 */
fun deco(emoji: String, before: Boolean = true): String =
    if (!Looks.scrapbook) "" else if (before) "$emoji " else " $emoji"
