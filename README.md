# Material Mascot

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

A living bot avatar for Android / Jetpack Compose — a faithful Kotlin/Canvas port of **bloub**, the MIT-licensed SVG recreation of the x.ai Grok avatar. Everything is drawn at runtime — no image assets — as one filled silhouette with two capsule eyes, morphing between poses with measured, frame-by-frame-fitted geometry.

## Features

- **Zero assets**: single filled silhouette, 64-point radial profiles, capsule eyes on a sphere
- **20+ emotional states**: Idle, Listening, Thinking, Working, Responding, Alert, Happy, Confused, Sleepy, Notify, Narrating, Sad, Love, Silly, Egg, Hexagon, Play, Burst, Comet, Exclaim, Orbit, Swirl
- **Deterministic animation**: pure function of time — pause, resume, seek and slow-mo produce identical frames
- **Life at rest**: gaze drift, saccades, glances, blinks, micro-fidgets
- **5 eye personalities**: Classic, Big, Round, Dot, Vizor — applied as a transform so expressions keep their character
- **Living-companion mode**: app-wide mood bus, shake-to-dizzy sensor reaction, single big-companion stage claim (`live` package, first-class public API)
- **Accessibility first**: `motion = false` kills decorative motion but keeps blinking/breathing essentials (WCAG reduced-motion)
- **Tap interaction**: tap the bot → it winks at you (Happy state for ~1.1 s)

## Requirements

| | |
|---|---|
| Min SDK | 26 |
| Compile SDK | 36 |
| Kotlin | 2.2.0+ |
| Compose (BOM era) | Foundation/UI 1.9+, Material3 |

## Setup

Coordinates: `com.maxmaster.materialmascot:mascot:1.0.0`

Until the artifact is on a remote repository, publish it locally once and consume it via `mavenLocal()`:

```bash
git clone https://github.com/maxmaster/material-mascot.git
cd material-mascot
./gradlew :mascot:publishToMavenLocal
```

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()          // picks up com.maxmaster.materialmascot:mascot
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.maxmaster.materialmascot:mascot:1.0.0")
}
```

Or via version catalog (`gradle/libs.versions.toml`):

```toml
[versions]
materialMascot = "1.0.0"

[libraries]
material-mascot = { module = "com.maxmaster.materialmascot:mascot", version.ref = "materialMascot" }
```

## Quick Start

The simplest entry point is [`MaterialBot`](mascot/src/main/java/com/maxmaster/materialmascot/ui/MaterialBot.kt): give it a `MascotConfig`, get an autonomously animating bot.

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.config.MascotConfig
import com.maxmaster.materialmascot.engine.BotState
import com.maxmaster.materialmascot.ui.MaterialBot

@Composable
fun MyBot() {
    var botState by remember { mutableStateOf(BotState.Idle) }

    // listening → thinking → responding → happy → idle
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
            color = Color(0xFF0A0A0C),   // bloub 'encre' ink
            size = 120.dp
        ),
        contentDescription = "Chat companion"
    )
}
```

## The Living Companion: LiveBot

[`LiveBot`](mascot/src/main/java/com/maxmaster/materialmascot/live/LiveBot.kt) wraps `MaterialBot` and adds app-wide life: exactly one place decides what the bot feels.

1. A hard shake always wins → Silly face + wobble.
2. An explicit `state` parameter overrides everything else.
3. Otherwise the shared **status bus** drives the mood — one `begin()` call moves *every* `LiveBot` on screen at once.

Install once at your activity root:

```kotlin
import com.maxmaster.materialmascot.live.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // One controller per activity; buzz respects your own setting.
            val shakeBuzzEnabled = remember { mutableStateOf(true) }
            val shakeDizzy = remember {
                ShakeDizzyController(this, vibrationEnabled = { shakeBuzzEnabled.value })
            }
            ShakeDizzyEffect(shakeDizzy)

            val statusBus = remember { MascotStatusBus() }

            CompositionLocalProvider(
                LocalShakeDizzy provides shakeDizzy,
                LocalMascotStatus provides statusBus,
            ) {
                App()
            }
        }
    }
}
```

Then render bots anywhere below that point:

```kotlin
LiveBot(
    config = MascotConfig(size = 96.dp),
    modifier = Modifier.align(Alignment.BottomEnd),
    contentDescription = "Companion"
)
```

Report real work through the bus and every visible companion reacts:

```kotlin
val token = statusBus.begin("chat")   // all bots start Working
try {
    sendMessage()
    statusBus.success(token)          // transient Happy, then back to Idle
} catch (t: Throwable) {
    statusBus.error(token)            // transient Alert
}

// Long-running ops escalate automatically: WORKING → SLOW after 10 s.
```

Both composition locals are nullable by design: where they're not provided, `LiveBot` degrades gracefully (no wobble, static config state).

### One Big Companion: BotStage

Screens that show their own large bot can claim the "big companion" slot so floating hosts and scaffolds hide their duplicate companion while the big one is visible:

```kotlin
import com.maxmaster.materialmascot.live.BotStage

@Composable
fun ChatScreen(bigBot: @Composable () -> Unit) {
    DisposableEffect(Unit) {
        val claim = BotStage.enter()
        onDispose { BotStage.exit(claim) }   // overlapping claims are ref-counted
    }
    if (BotStage.bigCompanionOnStage) bigBot()   // snapshot-backed: recomposes same frame
}
```

### Custom Renderers

Rendering `MaterialBot` yourself? You still get shake reactions à la carte:

```kotlin
val dizzy = LocalShakeDizzy.current
MaterialBot(
    config = MascotConfig(
        state = if (dizzy != null && dizzy.level.floatValue > DIZZY_FACE_THRESHOLD)
            BotState.Silly else myState
    ),
    modifier = Modifier.dizzyWobble(dizzy, enabled = motionAllowed)
)
```

## Customization

### Color & Shape

Shape is a state: any `BotState` morphs smoothly into any other, including body-shape states.

```kotlin
MascotConfig(
    state = BotState.Egg,             // also Hexagon, Play (triangle), Burst, Comet…
    color = Color(0xFF2496E8),        // body fill
    eyeColor = Color.White,           // eye/dot fill
    size = 120.dp
)
```

### Eye Personality

```kotlin
MascotConfig(state = BotState.Idle, eyeStyle = EyeStyle.BIG)
// CLASSIC tall capsules (default) · BIG grokbots look · ROUND cartoon circles
// DOT beady dots · VIZOR deadpan visor slits
```

Eye styles transform whatever the current pose prescribes, so expressions keep their character (Happy squints stay squints).

### Motion & Energy

```kotlin
MascotConfig(
    energy = 1.0f,   // calm (reference video) … 1.9f default (cheerful) … higher
    motion = true    // false = WCAG reduced-motion: decorative motion off,
                     // blink/gaze essentials stay alive
)
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
| `energy` | `Float` | `1.9f` | Liveliness multiplier (~1.0–2.5) |
| `eyeStyle` | `EyeStyle` | `CLASSIC` | Eye personality |

## BotState Reference

| State | Description | Morph | Periodic |
|-------|-------------|-------|----------|
| `Idle` | Calm breathing ball | 0.32s | 1.7s bob |
| `Listening` | Wide surprised eyes | 0.38s | Static |
| `Thinking` | 3 pulsing dots orbiting belly, no face | 0.28s | 2.4s orbit |
| `Working` | Same animation as Thinking | 0.28s | 2.4s orbit |
| `Responding` | Lively ball, raised gaze | 0.32s | 0.8s bounce |
| `Alert` | Stretched ball vibrating at 2.5 Hz | 0.32s | 400ms buzz |
| `Happy` | Wink | 0.22s | Static |
| `Confused` | Mismatched mirrored tilts | 0.28s | Static |
| `Sleepy` | Small breathing ball + dream bubble | 0.5s | 2.8s cycle |
| `Notify` | Round eyes + blue badge pop | 0.35s | Static |
| `Narrating` | Reading aloud, lowered gaze | 0.32s | 1.1s bob |
| `Sad` | Droopy mirrored tilts, sigh rhythm | 0.35s | 3.2s cycle |
| `Love` | Round shining eyes, giddy sway | 0.28s | 1.4s sway |
| `Silly` | Crossed mismatched tilts, goofball | 0.3s | Static |
| `Egg` | Egg-shaped body | 0.4s | Static |
| `Hexagon` | Hexagonal body | 0.4s | Static |
| `Play` | Tumbling rounded triangle | 0.5s | 3s tumble |
| `Burst` | Collapse to pebble + spiral sparkles | 0.4s | 2.6s cycle |
| `Comet` | Shrink to comet dot + trail | 0.45s | 2.4s cycle |
| `Exclaim` | Upright "!" glyph | 0.45s | Static |
| `Orbit` | Triangle tumble + concentric rings | 0.6s | 3.4s cycle |
| `Swirl` | Compressed ring flourish | 0.35s | 1s cycle |

> Tip: `BotState.fromName("thinking")` does a case-insensitive lookup and falls back to `Idle`.

## Public API Surface

**config/** — `MascotConfig`
**ui/** — `MaterialBot`
**engine/** — `BotEngine`, `BotFrame`, `RenderedEye`, `NotifBadge`, `DotSpec`, `NotifSpec`, `ArcSpec`, `BotState` (+22 objects), `EyeStyle`
**live/** — `LiveBot`, `MascotStatusBus`, `MascotStatus`, `MascotBusState`, `ShakeDizzyController`, `ShakeDizzyEffect`, `dizzyWobble`, `DIZZY_FACE_THRESHOLD`, `BotStage`, `LocalMascotStatus`, `LocalShakeDizzy`

Everything under `engine` that is geometry/math plumbing (`Pose`, `Silhouette`, `Face`, `HeadGaze`, `EyeCfg`, easings, PRNG) is `internal` and not part of the API contract. The `sample` package is `internal` too.

## ProGuard / R8

Consumer rules ship inside the AAR (`consumer-rules.pro`) and pin only the public API surface — no androidx/coroutines keeps leak into your app. Nothing here uses reflection, so R8 full mode works out of the box.

## Building Locally

```bash
./gradlew :mascot:testDebugUnitTest     # unit tests
./gradlew :mascot:assembleRelease       # release AAR -> mascot/build/outputs/aar/
./gradlew :mascot:publishToMavenLocal   # AAR + POM + sources + javadoc jars
```

## License

```
MIT License

Copyright (c) 2026 Max Master

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
