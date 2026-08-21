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

private data class EyePose(
    val open: Float,
    val widthScale: Float,
    val leftScale: Float,
    val rightScale: Float,
    val leftTilt: Float,
    val rightTilt: Float,
    val leftOffsetX: Float,
    val rightOffsetX: Float,
    val leftOffsetY: Float,
    val rightOffsetY: Float,
    val curious: Float,
    val happy: Float,
    val sad: Float,
    val alert: Float,
    val error: Float,
    val focused: Float,
    val confused: Float,
    val gazeX: Float,
    val gazeY: Float
)

/** Keep persisted/user-provided values from poisoning Canvas coordinates. */
internal fun normalizedMotionScale(value: Float): Float =
    if (value.isNaN()) 1f else value.coerceIn(0f, 2f)

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

    val motionScale = normalizedMotionScale(config.motionScale)
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

    val gazeTargetX = if (config.motion && motionScale > 0f) {
        ((sin(gazeStep.toDouble() * 1.37).toFloat() * 0.62f +
            sin(gazeStep.toDouble() * 0.41 + 0.8).toFloat() * 0.2f) * motionScale).coerceIn(-1.2f, 1.2f)
    } else 0f
    val gazeTargetY = if (config.motion && motionScale > 0f) {
        ((cos(gazeStep.toDouble() * 1.11 + 0.5).toFloat() * 0.35f +
            sin(gazeStep.toDouble() * 0.29).toFloat() * 0.16f) * motionScale).coerceIn(-0.8f, 0.8f)
    } else 0f
    val gazeDuration = (720f / motionScale.coerceAtLeast(0.35f)).toInt().coerceIn(320, 1600)
    val gazeSpec: FiniteAnimationSpec<Float> = if (config.motion && motionScale > 0f) {
        tween(durationMillis = gazeDuration, easing = FastOutSlowInEasing)
    } else snap()
    val gazeX by animateFloatAsState(gazeTargetX, gazeSpec, label = "mascot-gaze-x")
    val gazeY by animateFloatAsState(gazeTargetY, gazeSpec, label = "mascot-gaze-y")

    // Expressions are continuous eye poses. Each state is a semantic input
    // for hosts; the library morphs the eye geometry and body timing for them.
    val faceDuration = (360f / motionScale.coerceAtLeast(0.35f)).toInt().coerceIn(180, 900)
    val faceSpec: FiniteAnimationSpec<Float> = if (config.motion && motionScale > 0f) {
        tween(durationMillis = faceDuration, easing = FastOutSlowInEasing)
    } else snap()
    val curious by animateFloatAsState(if (config.expression == MascotExpression.CURIOUS) 1f else 0f, faceSpec, label = "mascot-curious")
    val happy by animateFloatAsState(if (config.expression == MascotExpression.HAPPY) 1f else 0f, faceSpec, label = "mascot-happy")
    val sleepy by animateFloatAsState(if (config.expression == MascotExpression.SLEEPY) 1f else 0f, faceSpec, label = "mascot-sleepy")
    val sad by animateFloatAsState(if (config.expression == MascotExpression.SAD) 1f else 0f, faceSpec, label = "mascot-sad")
    val alert by animateFloatAsState(if (config.expression == MascotExpression.ALERT) 1f else 0f, faceSpec, label = "mascot-alert")
    val error by animateFloatAsState(if (config.expression == MascotExpression.ERROR) 1f else 0f, faceSpec, label = "mascot-error")
    val focused by animateFloatAsState(if (config.expression == MascotExpression.FOCUSED) 1f else 0f, faceSpec, label = "mascot-focused")
    val thinking by animateFloatAsState(if (config.expression == MascotExpression.THINKING) 1f else 0f, faceSpec, label = "mascot-thinking")
    val excited by animateFloatAsState(if (config.expression == MascotExpression.EXCITED) 1f else 0f, faceSpec, label = "mascot-excited")
    val success by animateFloatAsState(if (config.expression == MascotExpression.SUCCESS) 1f else 0f, faceSpec, label = "mascot-success")
    val confused by animateFloatAsState(if (config.expression == MascotExpression.CONFUSED) 1f else 0f, faceSpec, label = "mascot-confused")

    val descriptionModifier = if (contentDescription == null) modifier
    else modifier.semantics { this.contentDescription = contentDescription }

    Canvas(descriptionModifier) {
        val accent = if (config.color != Color.Unspecified) config.color else Color(0xFF6D5ACF)
        val ink = Color.Black.copy(alpha = 0.72f)
        // At rest the book-wisp stays grounded; only a happy reply gets a
        // small celebratory body morph. Gaze drift and blinking carry idle life.
        // Resting states stay grounded; reactive states add distinct, bounded
        // timing: success/excited bounce, alert bursts, error shakes, and
        // confusion gently orbits. None overshoots the Material silhouette.
        val bodyMotion = maxOf(happy, sleepy * 0.22f, excited * 0.9f, success, alert * 0.7f, error * 0.45f, confused * 0.25f).coerceIn(0f, 1f)
        val burst = alert * maxOf(0f, sin(phase * 4f)) * motionScale
        val shake = error * sin(phase * 8f) * 2.4f * motionScale
        val orbit = confused * sin(phase * 1.35f) * 2.2f * motionScale
        val lift = if (config.motion) {
            bodyMotion * motionScale * (sin(phase * (1.2f + excited * 0.55f)) * size.minDimension * 0.022f) +
                burst * size.minDimension * 0.012f
        } else 0f
        // One quick natural blink per cycle; alert/success add a short wink-like
        // lid pulse, while reduced motion remains an exact resting pose.
        val calendarBlink = if (config.motion && motionScale > 0f) {
            ((sin(phase) - 0.93f) / 0.07f * motionScale).coerceIn(0f, 1f)
        } else 0f
        val stateBlink = if (config.motion && motionScale > 0f) {
            maxOf(
                alert * ((sin(phase * 3f + 0.7f) - 0.72f) / 0.28f * motionScale).coerceIn(0f, 1f),
                success * ((sin(phase * 2.4f + 1.4f) - 0.84f) / 0.16f * motionScale).coerceIn(0f, 1f)
            )
        } else 0f
        val blink = maxOf(calendarBlink, stateBlink)
        val eyePose = EyePose(
            open = ((1f - sleepy * 0.86f - sad * 0.5f - thinking * 0.16f - error * 0.2f) * (1f - blink) +
                alert * 0.2f).coerceIn(0.08f, 1.18f),
            // Give every semantic state a stable, independent silhouette.
            widthScale = (1f + happy * 0.12f - focused * 0.16f + alert * 0.1f - sleepy * 0.04f + excited * 0.16f + success * 0.06f).coerceIn(0.78f, 1.2f),
            leftScale = (1f + curious * 0.14f - happy * 0.08f - sad * 0.12f + alert * 0.12f - error * 0.12f - focused * 0.08f + thinking * 0.08f + excited * 0.18f + success * 0.1f - confused * 0.08f).coerceIn(0.68f, 1.28f),
            rightScale = (1f - curious * 0.1f + happy * 0.04f - sad * 0.2f + alert * 0.2f + error * 0.12f - focused * 0.14f + thinking * 0.2f + excited * 0.12f + success * 0.02f + confused * 0.12f).coerceIn(0.62f, 1.3f),
            leftTilt = (-12f * curious + 10f * happy + 7f * sad - 2f * alert - 8f * error - 16f * focused + 18f * thinking + 14f * excited + 6f * success + 22f * confused + error * sin(phase * 8f) * 5f),
            rightTilt = (12f * curious + 10f * happy - 8f * sad + 2f * alert + 8f * error + 16f * focused - 13f * thinking - 14f * excited - 7f * success - 22f * confused - error * sin(phase * 8f + 0.8f) * 5f),
            leftOffsetX = (-curious * 1.2f + error * 1.8f + focused * 1.2f - excited * 1.8f + confused * 2.8f),
            rightOffsetX = (curious * 1.2f - error * 1.8f - focused * 1.2f + excited * 1.8f - confused * 2.8f),
            leftOffsetY = (-thinking * 3.5f + sad * 1.5f + confused * 2.8f + error * sin(phase * 8f) * 0.9f),
            rightOffsetY = (thinking * 3.5f - sad * 1.5f - confused * 2.8f + error * sin(phase * 8f + 0.8f) * 0.9f),
            curious = (curious + focused * 0.72f + thinking * 0.56f + confused * 0.3f + error * 0.25f).coerceIn(0f, 1.3f),
            happy = (happy + excited * 0.65f + success * 0.82f).coerceIn(0f, 1.3f),
            sad = sad,
            alert = alert,
            error = error,
            focused = focused,
            confused = confused,
            gazeX = (gazeX + thinking * 0.12f + confused * 0.08f).coerceIn(-1f, 1f),
            gazeY = (gazeY + sad * 0.18f - thinking * 0.16f - alert * 0.08f).coerceIn(-1f, 1f)
        )
        val center = Offset(size.width / 2f, size.height / 2f + lift)
        // The tallest silhouette (the book-wisp bookmark) has only a small
        // margin at the edge of a square Canvas. Keep expressive shake/orbit
        // motion inside that margin even at the maximum configured scale.
        val tilt = (if (config.motion) {
            bodyMotion * motionScale * (sin(phase * 0.5f + 0.4f) * 1.8f + gazeX * 1.1f) +
                shake + orbit
        } else 0f).coerceIn(-12f, 12f)
        val squash = if (config.motion) {
            bodyMotion * motionScale * sin(phase * 2f) * 0.018f + burst * 0.025f
        } else 0f

        withTransform({
            rotate(tilt, center)
            scale(1f - squash, 1f + squash, center)
        }) {
            when (config.style) {
                MascotStyle.BOOK_WISP ->
                    drawBookWisp(center, size.minDimension, accent, ink, eyePose)
                MascotStyle.SPARK ->
                    drawSpark(center, size.minDimension, accent, ink, eyePose)
                MascotStyle.CIRCLE ->
                    drawCircleBody(center, size.minDimension, accent, ink, eyePose)
                MascotStyle.SOFT_BLOB ->
                    drawSoftBlob(center, size.minDimension, accent, ink, eyePose)
                MascotStyle.HEXAGON ->
                    drawHexagon(center, size.minDimension, accent, ink, eyePose)
                MascotStyle.TRIANGLE ->
                    drawTriangle(center, size.minDimension, accent, ink, eyePose)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircleBody(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eye: EyePose
) {
    val unit = diameter / 100f
    drawCircle(accent, radius = 33f * unit, center = center)
    drawCircle(
        color = accent.copy(alpha = 0.28f),
        radius = 33f * unit,
        center = center,
        style = Stroke(1.4f * unit)
    )
    drawEyes(center, unit, ink, eye)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoftBlob(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eye: EyePose
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
    drawEyes(center, unit, ink, eye)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexagon(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eye: EyePose
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
    drawEyes(center, unit, ink, eye)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTriangle(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eye: EyePose
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
    drawEyes(eyeCenter, unit * 0.86f, ink, eye)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBookWisp(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eye: EyePose
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
    drawEyes(center, unit, ink, eye)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpark(
    center: Offset,
    diameter: Float,
    accent: Color,
    ink: Color,
    eye: EyePose
) {
    val unit = diameter / 100f
    drawCircle(accent, radius = 31f * unit, center = center)
    val rays = listOf(-PI / 2, 0.0, PI / 2, PI)
    rays.forEach { angle ->
        val start = Offset(center.x + sin(angle.toFloat()) * 34f * unit, center.y - kotlin.math.cos(angle).toFloat() * 34f * unit)
        val end = Offset(center.x + sin(angle.toFloat()) * 41f * unit, center.y - kotlin.math.cos(angle).toFloat() * 41f * unit)
        drawLine(accent.copy(alpha = 0.72f), start, end, strokeWidth = 3f * unit)
    }
    drawEyes(center, unit, ink, eye)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyes(
    center: Offset,
    unit: Float,
    ink: Color,
    eyes: EyePose
) {
    // Eye-only semantic language: wide alert, narrow focused, asymmetric
    // thinking/confused poses, and calm shared gaze. White capsules stay
    // pupil-free so the face remains friendly at small sizes.
    val eyeWhite = Color.White.copy(alpha = 0.97f)
    val split = eyes.confused * 1.1f
    val drift = Offset(eyes.gazeX * 1.35f * unit, eyes.gazeY * 1.05f * unit)
    val leftEye = Offset(center.x - (14f + split) * unit + drift.x + eyes.leftOffsetX * unit, center.y - 5f * unit + drift.y + eyes.leftOffsetY * unit)
    val rightEye = Offset(center.x + (14f + split) * unit + drift.x + eyes.rightOffsetX * unit, center.y - 5f * unit + drift.y + eyes.rightOffsetY * unit)
    val leftHeight = (17f * eyes.open * (1f + 0.08f * eyes.curious + 0.04f * eyes.happy -
        0.08f * eyes.sad + 0.08f * eyes.alert) * eyes.leftScale).coerceAtLeast(1.6f) * unit
    val rightHeight = (17f * eyes.open * (1f - 0.06f * eyes.curious + 0.04f * eyes.happy -
        0.1f * eyes.sad + 0.08f * eyes.alert) * eyes.rightScale).coerceAtLeast(1.6f) * unit
    val width = (12.5f * eyes.widthScale).coerceAtLeast(8.4f) * unit

    fun drawEye(eye: Offset, height: Float, angle: Float) {
        withTransform({ rotate(angle, eye) }) {
            drawRoundRect(
                color = eyeWhite,
                topLeft = Offset(eye.x - width / 2f, eye.y - height / 2f),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2f, width / 2f)
            )
            if (eyes.open < 0.86f) {
                drawLine(
                    color = ink.copy(alpha = 0.5f),
                    start = Offset(eye.x - width * 0.34f, eye.y),
                    end = Offset(eye.x + width * 0.34f, eye.y),
                    strokeWidth = (1.25f + (0.86f - eyes.open) * 1.4f) * unit
                )
            }
        }
    }

    drawEye(leftEye, leftHeight, eyes.leftTilt)
    drawEye(rightEye, rightHeight, eyes.rightTilt)
}
