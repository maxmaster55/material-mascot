# Material Mascot

Material Mascot is a small, original Android Compose library for a friendly
book-wisp companion. It draws its character with Compose Canvas and Material 3
colors, so it has no image assets and naturally follows a host app theme.

The API is intentionally compact: `MaterialMascot` accepts `MascotConfig`,
which controls enablement, color, style, expression, and gentle motion. Hosts
can turn motion off for reduced-motion preferences or use the static config in
screens where a companion would be distracting.

Expression changes smoothly morph the eyes; the library never draws a mouth.
Available semantic states include CURIOUS, HAPPY, SLEEPY, SAD, ALERT, ERROR,
FOCUSED, THINKING, EXCITED, SUCCESS, and CONFUSED. Motion adds state-specific
eye timing, blink pulses, bounded bounce/orbit/shake, and smooth transitions.
Reduced motion keeps every state deterministic and still. An app can react to
status directly—for example, THINKING while retrieving, ALERT for attention,
SUCCESS after a reply, or SAD when no passage was found—without managing
animation state itself.

This project is independent code and artwork. It does not copy the shapes,
assets, or animation implementation of any other mascot project.

## Usage

Include the `mascot` Android library module, then render it from Compose:

```kotlin
MaterialMascot(
    config = MascotConfig(
        style = MascotStyle.BOOK_WISP,
        expression = MascotExpression.HAPPY,
        color = MaterialTheme.colorScheme.primary,
        motion = true,
    ),
)
```

All configuration is immutable and host-owned, so it can be backed by
DataStore, an in-memory chat state, or any other settings system.

## Local development

```sh
./gradlew :mascot:testDebugUnitTest :mascot:assembleDebug
```

The `preview` package contains a Material 3 `@Preview` for quick visual review.
