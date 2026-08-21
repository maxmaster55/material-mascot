package com.skald.materialmascot

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws an original book-wisp companion with Compose primitives only.
 *
 * The body is a rounded page lantern with a folded bookmark fin, rather than a
 * character asset. `motion = false` cancels the bob coroutine and draws the
 * exact resting pose, making reduced-motion behavior deterministic.
 */
@Composable
fun MaterialMascot(
    modifier: Modifier = Modifier.size(88.dp),
    config: MascotConfig = MascotConfig(),
    contentDescription: String? = "Reading companion"
) {
    if (!config.enabled) return

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(config.motion) {
        if (!config.motion) {
            phase = 0f
            return@LaunchedEffect
        }
        while (isActive) {
            val now = (System.nanoTime() / 1_000_000L) % 2200L
            phase = (now / 2200f) * (2f * PI.toFloat())
            delay(32L)
        }
    }

    // Expressions are continuous face poses, not swapped drawings. Hosts can
    // react to idle/thinking/reply state simply by changing `expression`; eyes
    // and mouth then morph without needing to coordinate their own animation.
    val faceSpec: FiniteAnimationSpec<Float> = if (config.motion) {
        tween(durationMillis = 360, easing = FastOutSlowInEasing)
    } else {
        snap()
    }
    val curious by animateFloatAsState(
        targetValue = if (config.expression == MascotExpression.CURIOUS) 1f else 0f,
        animationSpec = faceSpec,
        label = "mascot-curious"
    )
    val happy by animateFloatAsState(
        targetValue = if (config.expression == MascotExpression.HAPPY) 1f else 0f,
        animationSpec = faceSpec,
        label = "mascot-happy"
    )
    val sleepy by animateFloatAsState(
        targetValue = if (config.expression == MascotExpression.SLEEPY) 1f else 0f,
        animationSpec = faceSpec,
        label = "mascot-sleepy"
    )

    val descriptionModifier = if (contentDescription == null) modifier
    else modifier.semantics { this.contentDescription = contentDescription }

    Canvas(descriptionModifier) {
    val accent = if (config.color != Color.Unspecified) config.color else Color(0xFF6D5ACF)
        val ink = Color.Black.copy(alpha = 0.72f)
        val lift = if (config.motion) sin(phase) * size.minDimension * 0.035f else 0f
        // One quick natural blink per bob cycle. Sleepy scales the eyes down
        // independently, so changing expression and blinking compose cleanly.
        val blink = if (config.motion) {
            ((sin(phase) - 0.93f) / 0.07f).coerceIn(0f, 1f)
        } else 0f
        val eyeOpen = ((1f - sleepy * 0.86f) * (1f - blink)).coerceIn(0.08f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f + lift)
        when (config.style) {
            MascotStyle.BOOK_WISP ->
                drawBookWisp(center, size.minDimension, accent, ink, eyeOpen, curious, happy, sleepy)
            MascotStyle.SPARK ->
                drawSpark(center, size.minDimension, accent, ink, eyeOpen, curious, happy, sleepy)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBookWisp(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eyeOpen: Float,
    curious: Float,
    happy: Float,
    sleepy: Float
) {
    val unit = diameter / 100f
    val body = Rect(
        center.x - 34f * unit,
        center.y - 25f * unit,
        center.x + 34f * unit,
        center.y + 29f * unit
    )
    drawRoundRect(
        color = accent,
        topLeft = body.topLeft,
        size = body.size,
        cornerRadius = CornerRadius(18f * unit, 18f * unit)
    )

    // A split page edge and one asymmetric bookmark make this silhouette
    // distinctive while staying clear at 32dp and above.
    val fin = Path().apply {
        moveTo(center.x - 10f * unit, center.y - 24f * unit)
        lineTo(center.x - 4f * unit, center.y - 43f * unit)
        lineTo(center.x + 4f * unit, center.y - 30f * unit)
        lineTo(center.x + 13f * unit, center.y - 39f * unit)
        lineTo(center.x + 17f * unit, center.y - 20f * unit)
        close()
    }
    drawPath(fin, accent)
    drawLine(
        color = accent.copy(alpha = 0.45f),
        start = Offset(center.x, center.y - 21f * unit),
        end = Offset(center.x, center.y + 22f * unit),
        strokeWidth = 1.5f * unit
    )
    drawEyes(center, unit, ink, eyeOpen, curious)
    drawExpression(center, unit, ink, curious, happy, sleepy)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpark(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eyeOpen: Float,
    curious: Float,
    happy: Float,
    sleepy: Float
) {
    val unit = diameter / 100f
    drawCircle(accent, radius = 31f * unit, center = center)
    val rays = listOf(-PI / 2, 0.0, PI / 2, PI)
    rays.forEach { angle ->
        val start = Offset(center.x + sin(angle.toFloat()) * 34f * unit, center.y - kotlin.math.cos(angle).toFloat() * 34f * unit)
        val end = Offset(center.x + sin(angle.toFloat()) * 41f * unit, center.y - kotlin.math.cos(angle).toFloat() * 41f * unit)
        drawLine(accent.copy(alpha = 0.72f), start, end, strokeWidth = 3f * unit)
    }
    drawEyes(center, unit, ink, eyeOpen, curious)
    drawExpression(center, unit, ink, curious, happy, sleepy)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyes(
    center: Offset,
    unit: Float,
    ink: Color,
    eyeOpen: Float,
    curious: Float
) {
    val leftHeight = (8f * eyeOpen * (1f + 0.18f * curious)).coerceAtLeast(1.2f)
    val rightHeight = (8f * eyeOpen * (1f - 0.12f * curious)).coerceAtLeast(1.2f)
    drawRoundRect(
        color = ink,
        topLeft = Offset(center.x - 16f * unit, center.y - 4f * unit - leftHeight * unit / 2f),
        size = Size(6f * unit, leftHeight * unit),
        cornerRadius = CornerRadius(3f * unit)
    )
    drawRoundRect(
        color = ink,
        topLeft = Offset(center.x + 10f * unit, center.y - 4f * unit - rightHeight * unit / 2f),
        size = Size(6f * unit, rightHeight * unit),
        cornerRadius = CornerRadius(3f * unit)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExpression(
    center: Offset,
    unit: Float,
    ink: Color,
    curious: Float,
    happy: Float,
    sleepy: Float
) {
    if (curious > 0.001f) {
        drawOval(
            color = ink.copy(alpha = ink.alpha * curious),
            topLeft = Offset(center.x - 3.5f * unit, center.y + 3f * unit),
            size = Size(7f * unit, 8f * unit),
            style = Stroke((1.8f + 0.4f * curious) * unit)
        )
    }
    if (happy > 0.001f) {
        drawArc(
            color = ink.copy(alpha = ink.alpha * happy),
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(center.x - 11f * unit, center.y - 2f * unit),
            size = Size(22f * unit, 16f * unit),
            style = Stroke(2f * unit)
        )
    }
    if (sleepy > 0.001f) {
        drawLine(
            color = ink.copy(alpha = ink.alpha * sleepy),
            start = Offset(center.x - 5f * unit, center.y + 8f * unit),
            end = Offset(center.x + 5f * unit, center.y + 8f * unit),
            strokeWidth = 2f * unit
        )
    }
}
