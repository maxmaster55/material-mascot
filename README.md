# Material Mascot

A faithful Kotlin / Jetpack Compose Canvas port of **bloub**, the MIT-licensed
SVG recreation of the x.ai Grok avatar. Everything is drawn at runtime — no
image assets — as one filled silhouette with two capsule eyes, morphing between
poses with measured, frame-by-frame-fitted geometry.

## How it works

- **Single filled silhouette.** Every body shape is a radial profile r(theta)
  sampled at 64 fixed angles; any two shapes share points one-to-one, so a
  morph is just a linear interpolation of radii (plus shortest-path rotation).
- **Capsule eyes painted on a sphere.** Each eye sits on the tangent plane of
  an invisible head sphere, projected orthographically; edge compression and
  tilt fall out of the math. Resting gaze `REST_GAZE` = yaw 28.49°,
  pitch 28.62°, roll −13°, half-separation `EYE_SPLIT` = 15.46°, eye size
  `EYE_W` = 0.186 / `EYE_H` = 0.412 (ball-radius units).
- **Transitions** are exponential-style ease-outs (`easeOutQuint`,
  `easeInOutCubic`): fast settle, **no body overshoot**. The only springy
  effect is local to its state (Notify badge pop, eye opening).
- **Life at rest** is pure function of time: slow gaze drift plus a
  pre-computed deterministic blink schedule — first blink at 1.4 s, then blinks
  every 1.9–4.6 s, double-blink 18% of the time. Pause/resume/jump always give
  the identical image.
- **Notify badge** in bloub blue `#2496E8`, pop peak +14% around 0.3 s.

## States

The ten `BotState` objects map to bloub poses:

| BotState     | bloub pose                                  |
|--------------|---------------------------------------------|
| `Idle`       | idle ball                                    |
| `Listening`  | wide surprised eyes                          |
| `Thinking`   | three pulsing dots, no face                  |
| `Working`    | three pulsing dots, no face                  |
| `Responding` | lively ball, slightly raised gaze            |
| `Alert`      | tilted "!" glyph with travel + buzz          |
| `Happy`      | wink                                         |
| `Confused`   | mismatched mirrored-tilt eyes                |
| `Sleepy`     | tiny bouncing ball                           |
| `Notify`     | round wide eyes + notification badge         |

## Reduced motion

`MascotConfig(motion = false)` kills gaze wander, positional drift and
breathing and snaps transitions; blinking stays. The character remains alive
without movement, per accessibility guidelines.

## Usage

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.config.MascotConfig
import com.maxmaster.materialmascot.engine.BotState
import com.maxmaster.materialmascot.ui.MaterialBot

MaterialBot(
    config = MascotConfig(
        state = BotState.Thinking,
        color = Color(0xFF0A0A0C),
        size = 64.dp
    )
)
```

Defaults: `enabled = true`, `state`/`initialState = BotState.Idle`,
body color = ink `Color(0xFF0A0A0C)` (bloub 'encre'), eyes =
`Color.White`, `size = 64.dp`, `motion = true`. All fields are immutable so
the config can be backed by DataStore or ViewModel state.

In skald, chat status maps onto states: SUCCESS -> Happy, ERROR -> Alert,
SLOW -> Sleepy, WORKING -> Working, generating -> Thinking.

## Development

```sh
./gradlew :mascot:testDebugUnitTest :mascot:assembleDebug
```
