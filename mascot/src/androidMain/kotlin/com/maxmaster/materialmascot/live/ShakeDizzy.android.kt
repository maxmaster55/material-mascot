/*
 * Material Mascot — a living bot avatar for Compose apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.live

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.isActive

/**
 * "Shaken too hard" detector: accumulates dizziness from accelerometer spikes,
 * buzzes back lightly, and decays on its own so the bot sobers up in seconds.
 *
 * Android-only, because it is the accelerometer and the vibration motor that
 * make it work — the dizziness *state* it produces is common (see
 * [DizzySource]), so every platform can render a wobble even though only this
 * one can cause it.
 */
public class ShakeDizzyController(
    private val context: Context,
    /**
     * Queried live on every shake so hosts can expose the buzz as a runtime
     * setting. The dizziness itself always happens; this only silences the
     * vibration motor.
     */
    private val vibrationEnabled: () -> Boolean = { true }
) : SensorEventListener, DizzySource {

    override val level = mutableFloatStateOf(0f)

    override val phase = mutableFloatStateOf(0f)

    private var lastShakeAt = 0L

    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Accelerometer callback. A spike above [SHAKE_G_FORCE] counts as a shake;
     * events closer than [MIN_SHAKE_GAP_MS] are bounce, not intent.
     *
     * Detection uses the magnitude of total acceleration minus gravity, which
     * is orientation-INVARIANT: at rest the phone reads ~1 g whether upright,
     * sideways or face-down, so rotating or laying it down never trips the
     * trigger — only genuine linear jolts do.
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = event.values
        val magnitude = sqrt(x * x + y * y + z * z)
        val force = abs(magnitude - SensorManager.GRAVITY_EARTH)
        val now = System.currentTimeMillis()
        if (force < SHAKE_G_FORCE || now - lastShakeAt < MIN_SHAKE_GAP_MS) return
        lastShakeAt = now
        onShake((force - SHAKE_G_FORCE) / FORCE_RANGE)
    }

    /** One shake bump: escalate dizziness, buzz back with matching strength.
     *  The base bump is deliberately BELOW the face threshold: a single firm
     *  jerk only staggers the bot slightly — the Silly face needs SUSTAINED
     *  intense shaking, which piles up bumps far faster than decay removes
     *  them. */
    public fun onShake(strength: Float) {
        level.floatValue = min(level.floatValue + 0.22f + strength * 0.35f, MAX_LEVEL)
        if (!vibrationEnabled()) return
        val amp = (MIN_BUZZ + (MAX_BUZZ - MIN_BUZZ) *
            min(level.floatValue, 1f)).toInt()
        vibrator?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, BUZZ_MS), intArrayOf(0, amp), -1)
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Starts listening for shakes (call in ON_RESUME). */
    public fun start() {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /** Stops listening for shakes (call in ON_PAUSE). */
    public fun stop() {
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.unregisterListener(this)
    }

    private companion object {
        /**
         * Spike needed per accelerometer event. ~18 means roughly a full
         * extra g beyond gravity — a deliberate, intense shake. Everyday
         * jolts (picking up the phone, a bump while walking) stay far below
         * and never register.
         */
        const val SHAKE_G_FORCE = 18f

        /** Intensity normalizes over the range ABOVE the trigger: a 30g
         *  spike (violent shake) is strength 1.0. */
        const val FORCE_RANGE = 12f
        const val MIN_SHAKE_GAP_MS = 110L
        const val MAX_LEVEL = 1.5f
        const val BUZZ_MS = 45L
        const val MIN_BUZZ = 35f
        const val MAX_BUZZ = 110f
    }
}

/**
 * Installs the sensor listener for the current lifecycle and drives both the
 * wobble clock and the sobering-up decay while anyone is actually dizzy.
 *
 * Place this once at your activity root (or wherever the lifecycle is correct)
 * to enable shake-to-dizzy for all [LiveBot] instances.
 *
 * @param controller A [ShakeDizzyController] instance (typically created once per app).
 */
@Composable
public fun ShakeDizzyEffect(controller: ShakeDizzyController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.start()
                Lifecycle.Event.ON_PAUSE -> controller.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); controller.stop() }
    }

    // The single clock for dizziness: advance the wobble phase AND decay the
    // level while it is up, then stop ticking entirely once sober.
    LaunchedEffect(Unit) {
        var last = 0L
        withFrameNanos { last = it }
        while (isActive) {
            withFrameNanos { now ->
                val dt = (now - last) / 1_000_000_000f
                last = now
                if (controller.level.floatValue > 0f) {
                    controller.phase.floatValue += dt * WOBBLE_HZ
                    controller.level.floatValue =
                        (controller.level.floatValue - dt * SOBER_RATE).coerceAtLeast(0f)
                }
            }
        }
    }
}
