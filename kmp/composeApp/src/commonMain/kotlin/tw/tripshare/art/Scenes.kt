package tw.tripshare.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

/*
 * The hand-drawn trip scenes, ported from the Flutter app's SVGs: flat
 * palette fills, 3px ink outlines, rounded joins, no gradients. Drawn with
 * Compose's own canvas, so no SVG library is needed. Backdrops share a
 * 360 × 180 space; sprites (sun, cloud, vehicles) have their own small
 * size and are placed by TripScene.
 */

private val ink = Color(0xFF3E3A39)
private val paper = Color(0xFFFFFCF6)
private val coral = Color(0xFFF2A497)
private val yamabuki = Color(0xFFF6D27A)
private val mizu = Color(0xFFA8D3E6)
private val wakatake = Color(0xFFB9DAA5)
private val sky = Color(0xFFE3F1F7)
private val grass = Color(0xFFA3CE8C)
private val trunk = Color(0xFFC9A36A)
private val white = Color.White

/** One shape of a drawing: filled with [fill] (none when null), outlined with [stroke]. */
internal class Mark(
    val path: Path,
    val fill: Color?,
    val stroke: Color? = ink,
    val width: Float = 3f,
    val dash: FloatArray? = null,
)

private fun d(path: String, dx: Float = 0f) = PathParser().parsePathString(path).toPath().apply {
    if (dx != 0f) translate(Offset(dx, 0f))
}

private fun p(path: String, fill: Color?, stroke: Color? = ink, width: Float = 3f, dash: FloatArray? = null, dx: Float = 0f) =
    Mark(d(path, dx), fill, stroke, width, dash)

private fun rect(x: Float, y: Float, w: Float, h: Float, fill: Color?, rx: Float = 0f, width: Float = 3f) = Mark(
    Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(x, y, x + w, y + h, CornerRadius(rx, rx)))
    },
    fill, ink, width,
)

private fun circle(cx: Float, cy: Float, r: Float, fill: Color?, width: Float = 3f, dx: Float = 0f) = Mark(
    Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r + dx, cy - r, cx + r + dx, cy + r)) },
    fill, ink, width,
)

/** A drawing in its own coordinate space of [size]. */
class Drawing internal constructor(val size: Size, private val marks: List<Mark>) {
    /** Draws at the current origin, [scale] screen pixels per drawing unit. */
    fun draw(scope: DrawScope, scale: Float) = with(scope) {
        for (m in marks) {
            val path = Path().apply { addPath(m.path) }
            path.transform(androidx.compose.ui.graphics.Matrix().apply { scale(scale, scale) })
            m.fill?.let { drawPath(path, it, style = Fill) }
            if (m.stroke != null && m.width > 0f) {
                drawPath(
                    path, m.stroke,
                    style = Stroke(
                        width = m.width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = m.dash?.let { PathEffect.dashPathEffect(FloatArray(it.size) { i -> it[i] * scale }) },
                    ),
                )
            }
        }
    }
}

private fun drawing(w: Float, h: Float, vararg marks: Mark) = Drawing(Size(w, h), marks.toList())

/** Where a destination is, as far as the drawing goes. */
enum class SceneTheme { japan, korea, island, anywhere }

/** Guesses the scene from the trip's name; anything unrecognised gets the countryside with a plane. */
fun themeFor(tripName: String): SceneTheme {
    fun has(vararg words: String) = words.any { it in tripName }
    return when {
        has("日本", "東京", "大阪", "京都", "奈良", "北海道", "札幌", "福岡", "名古屋", "富士", "箱根", "九州", "四國",
            "關西", "關東", "輕井澤", "金澤", "廣島", "神戶", "仙台", "東北") -> SceneTheme.japan
        has("韓國", "首爾", "釜山", "濟州", "大邱", "仁川") -> SceneTheme.korea
        has("沖繩", "峇里", "巴里", "關島", "帛琉", "普吉", "蘇美", "宿霧", "長灘", "馬爾地夫", "夏威夷", "墾丁", "澎湖",
            "綠島", "蘭嶼", "小琉球", "海島", "海邊", "沙巴", "蘭卡威", "芽莊", "峴港") -> SceneTheme.island
        else -> SceneTheme.anywhere
    }
}

/** The vehicle's lane: its top edge in scene coordinates. Its size is the vehicle drawing's. */
fun laneTop(t: SceneTheme): Float = when (t) {
    SceneTheme.japan -> 129f
    SceneTheme.korea -> 116f
    SceneTheme.island -> 94f
    SceneTheme.anywhere -> 30f
}

val sun by lazy {
    drawing(
        48f, 48f,
        circle(24f, 24f, 18f, yamabuki),
        p("M17 25 q3 3 6 0 M25 25 q3 3 6 0", null, width = 2.5f),
    )
}

val cloud by lazy {
    drawing(76f, 36f, p("M14 30 a10 10 0 0 1 2-19 a14 14 0 0 1 26-4 a12 12 0 0 1 22 8 a8 8 0 0 1-2 15 z", white))
}

private val frame get() = rect(1.5f, 1.5f, 357f, 177f, sky, rx = 18f)
private const val GROUND_BOTTOM = "Q358.5 178.5 340 178.5 L20 178.5 Q1.5 178.5 1.5 160 Z"

fun backdrop(t: SceneTheme): Drawing = when (t) {
    SceneTheme.japan -> japan
    SceneTheme.korea -> korea
    SceneTheme.island -> island
    SceneTheme.anywhere -> anywhere
}

fun vehicle(t: SceneTheme): Drawing = when (t) {
    SceneTheme.japan -> shinkansen
    SceneTheme.korea -> bus
    SceneTheme.island -> boat
    SceneTheme.anywhere -> plane
}

private val japan by lazy {
    drawing(
        360f, 180f, frame,
        p("M60 150 L150 46 Q160 36 170 46 L262 150 Z", mizu),
        p("M131 68 L150 46 Q160 36 170 46 L189 68 L178 76 L168 66 L160 78 L150 66 L141 76 Z", white),
        p("M1.5 132 Q60 112 120 130 T240 128 T358.5 126 L358.5 160 L1.5 160 Z", wakatake),
        rect(276f, 96f, 8f, 46f, coral),
        rect(312f, 96f, 8f, 46f, coral),
        p("M266 88 Q298 80 330 88 L330 96 Q298 90 266 96 Z", coral),
        rect(272f, 102f, 52f, 7f, coral),
        p("M1.5 158 L358.5 158", null),
        p("M1.5 160 L358.5 160 L358.5 160 $GROUND_BOTTOM", paper),
        p("M20 168 L60 168 M90 168 L130 168 M160 168 L200 168 M230 168 L270 168 M300 168 L340 168", null, width = 2.5f),
    )
}

private val korea by lazy {
    drawing(
        360f, 180f, frame,
        p("M120 150 Q180 70 250 150 Z", wakatake),
        rect(181f, 44f, 10f, 56f, white),
        p("M170 44 L202 44 L198 30 L174 30 Z", coral),
        rect(183f, 16f, 6f, 14f, white),
        p("M186 16 L186 8", null),
        rect(176f, 98f, 20f, 14f, yamabuki),
        p("M1.5 140 Q50 120 100 138 Q112 144 118 150 L1.5 150 Z", wakatake),
        p("M250 150 Q262 140 280 134 Q320 120 358.5 132 L358.5 150 Z", wakatake),
        rect(24f, 118f, 40f, 26f, white),
        p("M16 120 Q44 100 72 120 Z", coral),
        rect(290f, 114f, 44f, 30f, white),
        p("M282 116 Q312 94 342 116 Z", mizu),
        p("M1.5 150 L358.5 150 L358.5 160 $GROUND_BOTTOM", paper),
    )
}

private val island by lazy {
    val dx = -60f
    drawing(
        360f, 180f, frame,
        p("M1.5 100 Q45 92 90 100 T180 100 T270 100 T358.5 100 L358.5 150 L1.5 150 Z", mizu),
        p("M30 122 q10 -6 20 0 M110 132 q10 -6 20 0 M230 118 q10 -6 20 0", null, width = 2.5f),
        p("M1.5 150 Q120 128 240 146 Q300 154 358.5 140 L358.5 160 $GROUND_BOTTOM", yamabuki),
        p("M300 150 Q296 110 308 70", null, width = 7f, dx = dx),
        p("M300 150 Q296 110 308 70", null, stroke = trunk, dx = dx),
        p("M308 70 Q280 56 262 76 Q286 66 308 70 Z", wakatake, dx = dx),
        p("M308 70 Q336 54 352 74 Q330 64 308 70 Z", wakatake, dx = dx),
        p("M308 70 Q296 42 272 44 Q296 52 308 70 Z", wakatake, dx = dx),
        p("M308 70 Q322 40 344 40 Q320 50 308 70 Z", wakatake, dx = dx),
        circle(300f, 74f, 4f, trunk, dx = dx),
        circle(312f, 76f, 4f, trunk, dx = dx),
        p("M60 160 q4 -10 8 0 q4 -10 8 0", coral, width = 2.5f),
    )
}

private val anywhere by lazy {
    drawing(
        360f, 180f, frame,
        p("M1.5 126 Q90 96 180 122 T358.5 112 L358.5 160 $GROUND_BOTTOM", wakatake),
        p("M1.5 150 Q120 132 240 150 T358.5 146 L358.5 160 $GROUND_BOTTOM", grass),
        rect(232f, 110f, 44f, 34f, white),
        p("M224 112 L254 90 L284 112 Z", coral),
        rect(248f, 124f, 12f, 20f, yamabuki),
        p("M84 140 L84 116", null),
        circle(84f, 108f, 14f, grass),
        p("M120 170 Q180 150 240 170", null, width = 2.5f, dash = floatArrayOf(4f, 8f)),
    )
}

// ---- Vehicles (facing right) -------------------------------------------------

private val shinkansen by lazy {
    drawing(
        120f, 30f,
        p("M4 26 L4 8 Q4 4 8 4 L82 4 Q110 4 116 22 Q117 26 112 26 Z", white),
        p("M4 18 L114 18", null, stroke = mizu, width = 5f),
        rect(14f, 8f, 12f, 6f, mizu, rx = 2f, width = 2.5f),
        rect(34f, 8f, 12f, 6f, mizu, rx = 2f, width = 2.5f),
        rect(54f, 8f, 12f, 6f, mizu, rx = 2f, width = 2.5f),
        p("M84 8 L96 8 Q102 10 104 14 L84 14 Z", mizu, width = 2.5f),
    )
}

private val bus by lazy {
    drawing(
        64f, 38f,
        rect(3f, 3f, 58f, 26f, yamabuki, rx = 8f),
        rect(9f, 8f, 10f, 9f, white, rx = 2f, width = 2.5f),
        rect(23f, 8f, 10f, 9f, white, rx = 2f, width = 2.5f),
        rect(37f, 8f, 10f, 9f, white, rx = 2f, width = 2.5f),
        rect(50f, 8f, 7f, 12f, white, rx = 2f, width = 2.5f),
        circle(16f, 30f, 5f, ink),
        circle(48f, 30f, 5f, ink),
    )
}

private val boat by lazy {
    drawing(
        60f, 44f,
        p("M30 4 L30 30", null),
        p("M32 6 L52 28 L32 28 Z", white),
        p("M28 10 L12 28 L28 28 Z", coral),
        p("M4 30 L56 30 L48 40 L12 40 Z", yamabuki),
    )
}

private val plane by lazy {
    drawing(
        64f, 30f,
        p("M6 16 Q6 10 14 10 L50 10 Q60 12 60 16 Q60 20 50 22 L14 22 Q6 22 6 16 Z", white),
        p("M26 12 L18 2 L26 2 L36 12 Z", coral),
        p("M26 20 L18 28 L26 28 L36 20 Z", coral),
        p("M8 12 L4 4 L10 4 L14 10 Z", mizu),
        circle(40f, 15f, 2f, mizu, width = 2f),
        circle(47f, 15f, 2f, mizu, width = 2f),
    )
}

/** Draws [d] with its top-left at ([x], [y]) in scene units. */
fun DrawScope.place(d: Drawing, x: Float, y: Float, scale: Float) {
    translate(x * scale, y * scale) { d.draw(this, scale) }
}
