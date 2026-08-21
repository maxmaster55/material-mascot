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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
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
    var gazeStep by remember { mutableIntStateOf(0) }

    // The body has a continuous clock, while gaze targets change more slowly.
    // Animating between those targets reads as attention rather than a looping
    // texture, and remains deterministic/offline for previews and tests.
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
    LaunchedEffect(config.motion) {
        if (!config.motion) {
            gazeStep = 0
            return@LaunchedEffect
        }
        while (isActive) {
            delay(1050L)
            gazeStep += 1
        }
    }

    val gazeTargetX = if (config.motion) {
        (sin(gazeStep.toDouble() * 1.37).toFloat() * 0.62f +
            sin(gazeStep.toDouble() * 0.41 + 0.8).toFloat() * 0.2f).coerceIn(-0.78f, 0.78f)
    } else 0f
    val gazeTargetY = if (config.motion) {
        (cos(gazeStep.toDouble() * 1.11 + 0.5).toFloat() * 0.35f +
            sin(gazeStep.toDouble() * 0.29).toFloat() * 0.16f).coerceIn(-0.55f, 0.55f)
    } else 0f
    val gazeSpec: FiniteAnimationSpec<Float> = if (config.motion) {
        tween(durationMillis = 720, easing = FastOutSlowInEasing)
    } else snap()
    val gazeX by animateFloatAsState(gazeTargetX, gazeSpec, label = "mascot-gaze-x")
    val gazeY by animateFloatAsState(gazeTargetY, gazeSpec, label = "mascot-gaze-y")

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
        // At rest the book-wisp stays grounded; only a happy reply gets a
        // small celebratory body morph. Gaze drift and blinking carry idle life.
        val bodyMotion = (happy + sleepy * 0.22f).coerceIn(0f, 1f)
        val lift = if (config.motion) {
            bodyMotion * sin(phase) * size.minDimension * 0.022f
        } else 0f
        // One quick natural blink per cycle. The reactive transform is subtle
        // enough for a header, but gives a reply a living silhouette.
        val blink = if (config.motion) {
            ((sin(phase) - 0.93f) / 0.07f).coerceIn(0f, 1f)
        } else 0f
        val eyeOpen = ((1f - sleepy * 0.86f) * (1f - blink)).coerceIn(0.08f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f + lift)
        val tilt = if (config.motion) {
            bodyMotion * (sin(phase * 0.5f + 0.4f) * 1.8f + gazeX * 1.1f)
        } else 0f
        val squash = if (config.motion) bodyMotion * sin(phase * 2f) * 0.018f else 0f

        withTransform({
            rotate(tilt, center)
            scale(1f - squash, 1f + squash, center)
        }) {
            when (config.style) {
                MascotStyle.BOOK_WISP ->
                    drawBookWisp(center, size.minDimension, accent, ink, eyeOpen, curious, happy, sleepy, gazeX, gazeY)
                MascotStyle.SPARK ->
                    drawSpark(center, size.minDimension, accent, ink, eyeOpen, curious, happy, sleepy, gazeX, gazeY)
                MascotStyle.CIRCLE ->
                    drawCircleBody(center, size.minDimension, accent, ink, eyeOpen, curious, happy, sleepy, gazeX, gazeY)
                MascotStyle.SOFT_BLOB ->
                    drawSoftBlob(center, size.minDimension, accent, ink, eyeOpen, curious, happy, sleepy, gazeX, gazeY)
                MascotStyle.HEXAGON ->
                    drawHexagon(center, size.minDimension, accent, ink, eyeOpen, curious, happy, sleepy, gazeX, gazeY)
                MascotStyle.TRIANGLE ->
                    drawTriangle(center, size.minDimension, accent, ink, eyeOpen, curious, happy, sleepy, gazeX, gazeY)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircleBody(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eyeOpen: Float,
    curious: Float,
    happy: Float,
    sleepy: Float,
    gazeX: Float,
    gazeY: Float
) {
    val unit = diameter / 100f
    drawCircle(accent, radius = 33f * unit, center = center)
    drawCircle(
        color = accent.copy(alpha = 0.28f),
        radius = 33f * unit,
        center = center,
        style = Stroke(1.4f * unit)
    )
    drawEyes(center, unit, ink, eyeOpen, curious, happy, gazeX, gazeY)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoftBlob(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eyeOpen: Float,
    curious: Float,
    happy: Float,
    sleepy: Float,
    gazeX: Float,
    gazeY: Float
) {
    val unit = diameter / 100f
    val blob = Path().apply {
        moveTo(center.x, center.y - 33f * unit)
        cubicTo(center.x + 23f * unit, center.y - 35f * unit, center.x + 36f * unit, center.y - 17f * unit, center.x + 33f * unit, center.y + 5f * unit)
        cubicTo(center.x + 31f * unit, center.y + 28f * unit, center.x + 13f * unit, center.y + 34f * unit, center.x - 5f * unit, center.y + 31f * unit)
        cubicTo(center.x - 28f * unit, center.y + 35f * unit, center.x - 38f * unit, center.y + 14f * unit, center.x - 32f * unit, center.y - 7f * unit)
        cubicTo(center.x - 29f * unit, center.y - 25f * unit, center.x - 14f * unit, center.y - 32f * unit, center.x, center.y - 33f * unit)
        close()
    }
    drawPath(blob, accent)
    drawEyes(center, unit, ink, eyeOpen, curious, happy, gazeX, gazeY)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexagon(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eyeOpen: Float,
    curious: Float,
    happy: Float,
    sleepy: Float,
    gazeX: Float,
    gazeY: Float
) {
    val unit = diameter / 100f
    val body = Path()
    repeat(6) { index ->
        val angle = -PI / 2.0 + index * PI / 3.0
        val point = Offset(
            center.x + cos(angle).toFloat() * 33f * unit,
            center.y + sin(angle).toFloat() * 33f * unit
        )
        if (index == 0) body.moveTo(point.x, point.y) else body.lineTo(point.x, point.y)
    }
    body.close()
    drawPath(body, accent)
    drawEyes(center, unit, ink, eyeOpen, curious, happy, gazeX, gazeY)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTriangle(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eyeOpen: Float,
    curious: Float,
    happy: Float,
    sleepy: Float,
    gazeX: Float,
    gazeY: Float
) {
    val unit = diameter / 100f
    val body = Path().apply {
        moveTo(center.x, center.y - 36f * unit)
        lineTo(center.x + 36f * unit, center.y + 28f * unit)
        cubicTo(center.x + 37f * unit, center.y + 31f * unit, center.x + 33f * unit, center.y + 34f * unit, center.x + 29f * unit, center.y + 34f * unit)
        lineTo(center.x - 29f * unit, center.y + 34f * unit)
        cubicTo(center.x - 33f * unit, center.y + 34f * unit, center.x - 37f * unit, center.y + 31f * unit, center.x - 36f * unit, center.y + 28f * unit)
        close()
    }
    drawPath(body, accent)
    // The sloped shoulders narrow near the apex; anchor the eyes lower and
    // slightly smaller so both large capsules stay inside the triangle.
    val eyeCenter = center.copy(y = center.y + 13f * unit)
    drawEyes(eyeCenter, unit * 0.86f, ink, eyeOpen, curious, happy, gazeX, gazeY)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBookWisp(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eyeOpen: Float,
    curious: Float,
    happy: Float,
    sleepy: Float,
    gazeX: Float,
    gazeY: Float
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
    drawEyes(center, unit, ink, eyeOpen, curious, happy, gazeX, gazeY)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpark(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eyeOpen: Float,
    curious: Float,
    happy: Float,
    sleepy: Float,
    gazeX: Float,
    gazeY: Float
) {
    val unit = diameter / 100f
    drawCircle(accent, radius = 31f * unit, center = center)
    val rays = listOf(-PI / 2, 0.0, PI / 2, PI)
    rays.forEach { angle ->
        val start = Offset(center.x + sin(angle.toFloat()) * 34f * unit, center.y - kotlin.math.cos(angle).toFloat() * 34f * unit)
        val end = Offset(center.x + sin(angle.toFloat()) * 41f * unit, center.y - kotlin.math.cos(angle).toFloat() * 41f * unit)
        drawLine(accent.copy(alpha = 0.72f), start, end, strokeWidth = 3f * unit)
    }
    drawEyes(center, unit, ink, eyeOpen, curious, happy, gazeX, gazeY)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyes(
    center: Offset,
    unit: Float,
    ink: Color,
    eyeOpen: Float,
    curious: Float,
    happy: Float,
    gazeX: Float,
    gazeY: Float
) {
    // Two large paper-white capsules carry the face. They drift together
    // toward one calm gaze target; their small size/tilt difference keeps the
    // expression warm without introducing uncanny eyeball-like pupils.
    val eyeWhite = Color.White.copy(alpha = 0.97f)
    val drift = Offset(gazeX * 1.35f * unit, gazeY * 1.05f * unit)
    val leftEye = Offset(center.x - 14f * unit + drift.x, center.y - 5f * unit + drift.y)
    val rightEye = Offset(center.x + 14f * unit + drift.x, center.y - 5f * unit + drift.y)
    val leftHeight = (17f * eyeOpen * (1f + 0.08f * curious + 0.04f * happy)).coerceAtLeast(1.6f) * unit
    val rightHeight = (17f * eyeOpen * (1f - 0.06f * curious + 0.04f * happy)).coerceAtLeast(1.6f) * unit

    fun drawEye(eye: Offset, height: Float, width: Float, angle: Float) {
        withTransform({ rotate(angle, eye) }) {
            drawRoundRect(
                color = eyeWhite,
                topLeft = Offset(eye.x - width / 2f, eye.y - height / 2f),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2f, width / 2f)
            )
            // The crease becomes the upper/lower lid as the capsule narrows,
            // making a blink legible without adding a separate character face.
            if (eyeOpen < 0.86f) {
                drawLine(
                    color = ink.copy(alpha = 0.5f),
                    start = Offset(eye.x - width * 0.34f, eye.y),
                    end = Offset(eye.x + width * 0.34f, eye.y),
                    strokeWidth = (1.25f + (0.86f - eyeOpen) * 1.4f) * unit
                )
            }
        }
    }

    drawEye(leftEye, leftHeight, 12.5f * unit * (1f + 0.05f * happy), 21f - 2f * curious - 1.5f * happy)
    drawEye(rightEye, rightHeight, 12.5f * unit * (1f + 0.05f * happy), 21f + 1f * curious + 1.5f * happy)
}
