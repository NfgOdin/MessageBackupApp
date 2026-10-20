package com.messagebackup.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.messagebackup.app.R
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private data class FeatherEmber(
    val relProgress: Float,
    val offsetX: Float,
    val offsetY: Float,
    val color: Color,
    val size: Float
)

/**
 * Signature Delight Animation: When dispatching a message or Raven Mesh packet,
 * Huginn launches directly from the send button and soars upward across the screen
 * in a majestic parabolic arc, leaving an ethereal runic feather trail.
 */
@Composable
fun HuginnFlightOverlay(
    isFlying: Boolean,
    onFlightCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isFlying) return

    val progress = remember { Animatable(0f) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Runic embers generated along the flight path
    val embers = remember {
        List(18) {
            FeatherEmber(
                relProgress = Random.nextFloat() * 0.7f + 0.1f,
                offsetX = (Random.nextFloat() - 0.5f) * 60f,
                offsetY = (Random.nextFloat() - 0.5f) * 40f,
                color = if (it % 2 == 0) Color(0xFF64FFDA) else Color(0xFFFFD54F),
                size = Random.nextFloat() * 4f + 3f
            )
        }
    }

    LaunchedEffect(isFlying) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
        onFlightCompleted()
    }

    val p = progress.value

    // Parabolic swoop from bottom-right (send button) sweeping towards center-top and off screen
    // Quadratic Bezier: P0 = Start, P1 = Control Point, P2 = End
    val startX = screenWidthPx * 0.88f
    val startY = screenHeightPx * 0.90f

    val controlX = screenWidthPx * 0.35f
    val controlY = screenHeightPx * 0.45f

    val endX = screenWidthPx * 0.15f
    val endY = -screenHeightPx * 0.15f

    val oneMinusP = 1f - p
    val currentX = (oneMinusP * oneMinusP * startX) + (2f * oneMinusP * p * controlX) + (p * p * endX)
    val currentY = (oneMinusP * oneMinusP * startY) + (2f * oneMinusP * p * controlY) + (p * p * endY)

    // Dynamic bank angle following trajectory + subtle wing-flap oscillation
    val bankAngle = -38f + (p * 12f) + (sin(p * 18f * PI.toFloat()) * 6f)
    val scale = (0.6f + (sin(p * PI.toFloat()) * 0.7f)).coerceIn(0.4f, 1.35f)
    val alpha = if (p < 0.1f) p / 0.1f else (1f - ((p - 0.75f) / 0.25f)).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        // Glowing feather trail
        Canvas(modifier = Modifier.fillMaxSize()) {
            embers.forEach { ember ->
                if (p >= ember.relProgress) {
                    val emberAlpha = (1f - (p - ember.relProgress) / 0.4f).coerceIn(0f, 0.8f)
                    if (emberAlpha > 0f) {
                        val emp = ember.relProgress
                        val omp = 1f - emp
                        val ex = (omp * omp * startX) + (2f * omp * emp * controlX) + (emp * emp * endX) + ember.offsetX
                        val ey = (omp * omp * startY) + (2f * omp * emp * controlY) + (emp * emp * endY) + ember.offsetY
                        drawCircle(
                            color = ember.color.copy(alpha = emberAlpha),
                            radius = ember.size * density.density,
                            center = Offset(ex, ey)
                        )
                    }
                }
            }
        }

        // Flying Raven Avatar
        Box(
            modifier = Modifier
                .offset { IntOffset(currentX.toInt() - 36, currentY.toInt() - 36) }
                .scale(scale)
                .rotate(bankAngle)
                .alpha(alpha)
        ) {
            // Ambient cyan glow halo around the bird
            Icon(
                painter = painterResource(id = R.drawable.ic_flying_raven),
                contentDescription = null,
                tint = Color(0xFF64FFDA).copy(alpha = 0.5f),
                modifier = Modifier
                    .size(54.dp)
                    .offset(x = 1.dp, y = 1.dp)
            )
            // Primary obsidian / gold bird icon
            Icon(
                painter = painterResource(id = R.drawable.ic_flying_raven),
                contentDescription = "Huginn in Flight",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}
