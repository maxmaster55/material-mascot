# Material Mascot

[![Maven Central](https://img.shields.io/maven-central/v/com.maxmaster.materialmascot/mascot)](https://central.sonatype.com/artifact/com.maxmaster.materialmascot/mascot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

A living bot avatar for Android / Jetpack Compose — a faithful Kotlin/Canvas port of **bloub**, the MIT-licensed SVG recreation of the x.ai Grok avatar. Everything is drawn at runtime — no image assets — as one filled silhouette with two capsule eyes, morphing between poses with measured, frame-by-frame-fitted geometry.

## Features

- **Zero assets**: Single filled silhouette, 64-point radial profiles, capsule eyes on a sphere
- **20+ emotional states**: Idle, Listening, Thinking, Working, Responding, Alert, Happy, Confused, Sleepy, Notify, Narrating, Sad, Love, Silly, Egg, Hexagon, Play, Burst, Comet, Exclaim, Orbit, Swirl
- **Deterministic animation**: Pure function of time — pause, resume, seek, and slow-mo all produce identical frames
- **Life at rest**: Gaze drift, saccades, glances, blinks, micro-fidgets — all pre-computed calendars
- **Exponential transitions**: `easeOutQuint` morphs with no body overshoot; chained transitions stay continuous
- **5 eye personalities**: Classic, Big, Round, Dot, Vizor — applied as a transform so expressions keep character
- **Accessibility first**: `motion = false` kills decorative motion but keeps blinking (WCAG reduced-motion)
- **Tap interaction**: Tap the bot → it winks at you (Happy state for 1.1s)

## Gradle Setup

### Using Version Catalog (recommended)

In your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Add your maven repository if not on Maven Central yet
        maven { url = uri("https://your-maven-repo.com") }
    }
    versionCatalogs {
        create("libs") {
            fromFiles(files("gradle/libs.versions.toml"))
        }
    }
}
```

In your `gradle/libs.versions.toml`:

```toml
[versions]
materialMascot = "1.0.0"

[libraries]
material-mascot = { module = "com.maxmaster.materialmascot:mascot", version.ref = "materialMascot" }
```

In your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.material.mascot)
}
```

### Direct Dependency

```kotlin
dependencies {
    implementation("com.maxmaster.materialmascot:mascot:1.0.0")
}
```

Requires:
- `minSdk 26+`
- `compileSdk 36+`
- Kotlin 2.2.0+
- Compose Compiler 2.2.0+

## Quick Start

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.config.MascotConfig
import com.maxmaster.materialmascot.engine.BotState
import com.maxmaster.materialmascot.ui.MaterialBot

@Composable
fun MyBot() {
    MaterialBot(
        config = MascotConfig(
            state = BotState.Thinking,
            color = Color(0xFF0A0A0C),   // bloub 'encre' ink
            size = 64.dp
        ),
        contentDescription = "Bot mascot thinking"
    )
}
```

### With State Transitions

```kotlin
@Composable
fun ChatBot() {
    var botState by remember { mutableStateOf(BotState.Idle) }

    // Simulate: listening → thinking → responding → happy → idle
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000); botState = BotState.Listening
            delay(1500); botState = BotState.Thinking
            delay(2000); botState = BotState.Responding
            delay(1500); botState = BotState.Happy
            delay(2000); botState = BotState.Idle
        }
    }

    MaterialBot(
        config = MascotConfig(
            state = botState,
            color = MaterialTheme.colorScheme.primary,
            size = 120.dp
        ),
        contentDescription = "Chat companion"
    )
}
```

## Customization

### Shape & Color

```kotlin
MascotConfig(
    state = BotState.Happy,
    color = Color(0xFF2496E8),      // Body color (notify blue)
    eyeColor = Color.White,          // Eye color
    size = 120.dp                    // Canvas size
)
```

### Eye Personality

```kotlin
import com.maxmaster.materialmascot.engine.EyeStyle

MascotConfig(
    state = BotState.Idle,
    eyeStyle = EyeStyle.BIG,       // Large curious eyes
    // EyeStyle.ROUND   // Circular cartoon eyes
    // EyeStyle.DOT     // Tiny beady dots
    // EyeStyle.VIZOR   // Horizontal visor slits
    // EyeStyle.CLASSIC // Default tall capsules
)
```

### Energy Level (Liveliness)

```kotlin
MascotConfig(
    state = BotState.Idle,
    energy = 1.0f,   // Calm (reference video)
    // energy = 1.9f  // Cheerful & active (default)
    // energy = 2.5f  // Maximum energy
)
```

### Reduced Motion (Accessibility)

```kotlin
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun AccessibleBot(state: BotState) {
    val reducedMotion = LocalConfiguration.current.fontScale > 1.0f // or use system setting
    MaterialBot(
        config = MascotConfig(
            state = state,
            motion = !reducedMotion  // Disables gaze drift, breathing, float; keeps blink
        )
    )
}
```

### All Configuration Options

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | `Boolean` | `true` | Show/hide the mascot |
| `state` | `BotState` | `Idle` | Current emotional state |
| `initialState` | `BotState` | `Idle` | State on first composition |
| `color` | `Color` | `0xFF0A0A0C` | Body fill (bloub 'encre') |
| `eyeColor` | `Color` | `White` | Eye fill color |
| `size` | `Dp` | `64.dp` | Canvas size |
| `motion` | `Boolean` | `true` | Enable decorative motion |
| `energy` | `Float` | `1.9f` | Liveliness multiplier (0.5–2.5) |
| `eyeStyle` | `EyeStyle` | `CLASSIC` | Eye personality |

## BotState Reference

| State | Description | Morph | Periodic |
|-------|-------------|-------|----------|
| `Idle` | Calm breathing ball | 0.32s | 1.7s bob |
| `Listening` | Wide surprised eyes | 0.38s | Static |
| `Thinking` | 3 pulsing dots, no face | 0.28s | 2.4s orbit |
| `Working` | Same as Thinking | 0.28s | 2.4s orbit |
| `Responding` | Lively ball, raised gaze | 0.32s | 0.8s bounce |
| `Alert` | Tilted "!" + buzz | 0.32s | 2.5Hz vibrate |
| `Happy` | Wink | 0.22s | Static |
| `Confused` | Mismatched mirrored tilts | 0.28s | Static |
| `Sleepy` | Small bouncing ball + dream bubble | 0.5s | 2.8s breathe |
| `Notify` | Round eyes + blue badge pop | 0.35s | Static |
| `Narrating` | Reading aloud, lowered gaze | 0.32s | 1.1s bob |
| `Sad` | Droopy mirrored tilts | 0.35s | 3.2s sigh |
| `Love` | Round shining eyes, giddy sway | 0.28s | 1.4s sway |
| `Silly` | Crossed mismatched tilts | 0.3s | Static |
| `Egg` | Egg-shaped body | 0.4s | Static |
| `Hexagon` | Hexagonal body | 0.4s | Static |
| `Play` | Tumbling triangle | 0.5s | 3s tumble |
| `Burst` | Collapse + spiral sparkles | 0.4s | 2.6s cycle |
| `Comet` | Shrink to dot + trail | 0.45s | 2.4s orbit |
| `Exclaim` | Upright "!" glyph | 0.45s | Static |
| `Orbit` | Triangle tumble + rings | 0.6s | 3.4s cycle |
| `Swirl` | Compressed ring flourish | 0.35s | 1s cycle |

> **Tip**: Use `BotState.fromName("thinking")` for dynamic state lookup (case-insensitive, falls back to `Idle`).

## Advanced: Custom Status Bus

The library includes an internal `MascotStatusBus` (WORKING → SLOW → SUCCESS/ERROR) used by the internal `LiveBot`. For your own app-wide status system, drive `MascotConfig.state` directly from your ViewModel, repository, or state holder:

```kotlin
@Composable
fun AppBot(viewModel: MyViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val botState = when (uiState) {
        is UiState.Loading -> BotState.Thinking
        is UiState.Success -> BotState.Happy
        is UiState.Error -> BotState.Alert
        is UiState.Empty -> BotState.Sleepy
        else -> BotState.Idle
    }

    MaterialBot(
        config = MascotConfig(state = botState, size = 80.dp),
        contentDescription = "App status"
    )
}
```

## Architecture

```
com.maxmaster.materialmascot
├── config/
│   └── MascotConfig          // Immutable host-owned configuration
├── engine/
│   ├── BotEngine             // Pure frame sampler (time → BotFrame)
│   ├── BotState              // 20+ sealed state objects
│   ├── EyeStyle              // 5 eye personality enums
│   ├── BotFrame              // Rendered frame (body, eyes, dots, arcs, badge)
│   ├── RenderedEye           // Eye ready to draw (affine + blinkK)
│   ├── BotMath               // Easings, noise, PRNG (internal)
│   ├── BotShape              // Silhouette, profiles, blending (internal)
│   ├── BotFace               // Sphere eyes, gaze, life calendars (internal)
│   └── Arcs                  // Decorative rings (internal)
├── ui/
│   └── MaterialBot           // Compose Canvas renderer + tap wink
├── live/                     // Internal: status bus, shake sensor, stage claiming
└── sample/                   // Internal: usage examples
```

**Public API** (stable): `MascotConfig`, `MaterialBot`, `BotState`, `EyeStyle`, `BotEngine`, `BotFrame`, `RenderedEye`, `NotifBadge`, `DotSpec`, `NotifSpec`, `TEAR_POINTS`, `NOTIF_BLUE` constants.

**Internal** (not for consumer use): `LiveBot`, `BotStage`, `MascotStatusBus`, `ShakeDizzyController`, `ShakeDizzyEffect`, `dizzyWobble`, `LocalMascotStatus`, `LocalShakeDizzy`.

## ProGuard / R8

The library includes consumer ProGuard rules (`consumer-rules.pro`) that keep the public API. No extra rules needed for typical usage.

If you use R8 full mode and see warnings, add:

```proguard
-keep class com.maxmaster.materialmascot.** { *; }
```

## Building Locally

```bash
# Run tests
./gradlew :mascot:testDebugUnitTest

# Build release AAR
./gradlew :mascot:assembleRelease

# Publish to local Maven
./gradlew :mascot:publishToMavenLocal
```

Output: `mascot/build/outputs/aar/mascot-release.aar`

## License

```
MIT License

Copyright (c) 2026 Skald contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Based on **bloub** (MIT) — SVG recreation of the x.ai Grok avatar.