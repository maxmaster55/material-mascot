# mascot module — bloub port

A faithful Kotlin / Jetpack Compose Canvas port of **bloub**, the MIT-licensed
SVG recreation of the x.ai Grok avatar. No image assets: one filled silhouette,
two capsule eyes, measured geometry, deterministic motion.

Note: source files live under `src/main/java/com/skald/materialmascot/` but the
declared packages are `com.maxmaster.materialmascot.*`.

## Layout

```
com.maxmaster.materialmascot/
├── engine/     # pure geometry + time (BotMath, BotShape, BotFace, BotState, BotEngine)
├── ui/         # MaterialBot.kt — Compose Canvas renderer + previews
├── config/     # MascotConfig.kt — immutable host-owned configuration
└── sample/     # SampleUsage.kt — chat UI integration example
```

## Engine facts

- **Body**: single filled silhouette; each pose is a radial profile r(theta)
  sampled at 64 angles, so morphs are linear radius interpolation with
  shortest-path rotation.
- **Eyes**: capsules painted on a sphere. Resting gaze `REST_GAZE`
  (yaw 28.49°, pitch 28.62°, roll −13°), half-separation `EYE_SPLIT` = 15.46°,
  size `EYE_W` = 0.186 / `EYE_H` = 0.412 in ball-radius units. Blink is a
  vertical screen-space squash of the capsule matrix.
- **Transitions**: exponential ease-outs (`easeOutQuint`, `easeInOutCubic`),
  no body overshoot. A change arriving mid-fade freezes the composite pose and
  blends from it, keeping chained transitions continuous.
- **Life at rest**: gaze drift plus a pre-computed deterministic blink
  schedule (`BLINKS`) — first blink 1.4 s, then every 1.9–4.6 s, double-blink
  18%. `BotEngine.sample()` is a pure function of time.
- **Notify**: badge in blue `#2496E8` (`NOTIF_BLUE`), pop peak +14%.

## States

| `BotState`   | bloub pose                          |
|--------------|-------------------------------------|
| `Idle`       | idle ball                           |
| `Listening`  | wide surprised eyes                 |
| `Thinking`   | three pulsing dots, no face         |
| `Working`    | three pulsing dots, no face         |
| `Responding` | lively ball, slightly raised gaze   |
| `Alert`      | tilted "!" glyph with travel + buzz |
| `Happy`      | wink                                |
| `Confused`   | mismatched mirrored-tilt eyes       |
| `Sleepy`     | tiny bouncing ball                  |
| `Notify`     | round wide eyes + badge             |

## API

```kotlin
@Composable
fun MaterialBot(
    config: MascotConfig,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
)

@Immutable
data class MascotConfig(
    val enabled: Boolean = true,
    val state: BotState = BotState.Idle,
    val initialState: BotState = BotState.Idle,
    val color: Color = Color(0xFF0A0A0C), // bloub 'encre' ink
    val eyeColor: Color = Color.White,
    val size: Dp = 64.dp,
    val motion: Boolean = true
)
```

## Reduced motion

`motion = false` kills gaze wander, positional drift and breathing and snaps
transitions; blinking stays.

## Integration

```kotlin
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

skald maps chat status onto states: SUCCESS -> Happy, ERROR -> Alert,
SLOW -> Sleepy, WORKING -> Working, generating -> Thinking.

## Tests

```sh
./gradlew :mascot:testDebugUnitTest
```

JVM unit tests cover the engine math (`MascotConfigTest`,
`engine/BotEngineTest`): blink schedule, easing endpoints/monotonicity,
circle profile, sample determinism, eye containment, chained-transition
purity and the `BotState` registry.
