package tw.tripshare.art

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import tw.tripshare.prefersReducedMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * The destination's picture: a still backdrop with a few parts that move
 * gently. Clouds drift, the sun bobs, a train, bus, boat or plane crosses.
 * Holds still when the phone asks for reduced motion.
 */
@Composable
fun TripScene(tripName: String, modifier: Modifier = Modifier) {
    val theme = themeFor(tripName)
    val calm = remember { prefersReducedMotion() }
    val clock by rememberInfiniteTransition().animateFloat(
        0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
    )
    val back = backdrop(theme)
    val car = vehicle(theme)
    val lane = laneTop(theme)
    Canvas(modifier.fillMaxWidth().aspectRatio(2f).semantics { contentDescription = "旅程插圖" }) {
        val t = if (calm) 0.45f else clock
        val k = size.width / 360f
        val wave = sin(t * 2 * PI).toFloat()
        val w = car.size.width
        val vx = -w + (360f + w) * t
        back.draw(this, k)
        place(sun, 292f, 14f + 3 * wave, k)
        place(cloud, 30f + 14 * wave, 22f, k)
        place(cloud, 196f - 10 * wave, 40f, k)
        val frame = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(18 * k)))
        }
        clipPath(frame) {
            place(car, vx, lane + if (theme == SceneTheme.island) 2 * wave else 0f, k)
        }
    }
}
