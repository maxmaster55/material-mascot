# Desktop overlay (mascot as a desktop pet) on GNOME / Wayland

Research notes for running the mascot as an always-on-top, transparent,
click-through-where-empty window on Ubuntu GNOME under Wayland.

Target environment these notes were checked against:

- `XDG_SESSION_TYPE=wayland`, `XDG_CURRENT_DESKTOP=ubuntu:GNOME`
- OpenJDK 21.0.11 (Ubuntu 24.04)
- Compose Multiplatform desktop (Skiko/Skia over AWT)

Claims below are tagged **[verified]** (measured on this machine),
**[reported]** (from upstream issues/docs) or **[inferred]** (reasoned, not
measured). Do not promote an inferred row to fact without a real run.

---

## Verdict

The window management this needs is **available and works** — but not for the
reason you would guess. JDK 21 has no Wayland backend at all, so the app runs
through XWayland automatically, and Mutter acts as its X11 window manager and
honours `_NET_WM_STATE_ABOVE`. Always-on-top, per-pixel transparency and
shaped (click-through) windows are all supported on this exact setup.

The real risk is **not** Wayland. It is Skiko: Compose Multiplatform has a
documented Linux failure where `transparent = true` + `undecorated = true`
fails to create a Skia OpenGL context and throws at window creation. Raw
AWT/Swing does the same job here without complaint, so the blocker (if it
bites) is Compose's renderer, not the compositor.

Recommendation: build it, but spike the transparent Compose window **first**,
before porting anything else. That single window is the whole risk.

---

## What works / what doesn't

| Capability | Result | Evidence |
|---|---|---|
| AWT toolkit in use | `sun.awt.X11.XToolkit` — i.e. XWayland, always | **[verified]** |
| `isAlwaysOnTopSupported()` | `true` | **[verified]** |
| `_NET_WM_STATE_ABOVE` actually applied by Mutter | yes — `xprop` shows `_NET_WM_STATE_ABOVE` on the live window | **[verified]** |
| `PERPIXEL_TRANSPARENT` | supported | **[verified]** |
| `TRANSLUCENT` | supported | **[verified]** |
| `PERPIXEL_TRANSLUCENT` | supported | **[verified]** |
| `setBackground(new Color(0,0,0,0))` on undecorated frame | OK, `isOpaque()` reads back `false` | **[verified]** |
| `setShape(Ellipse2D)` — clicks outside shape pass through | accepted without exception | **[verified]** |
| Compose `Window(transparent = true, undecorated = true)` on Linux | **known to fail** with `[SKIKO] warn: Failed to create Skia OpenGL context!` + NPE/RuntimeException | **[reported]** — [#3887](https://github.com/JetBrains/compose-multiplatform/issues/3887), [#3171](https://github.com/JetBrains/compose-multiplatform/issues/3171) |
| Transparent window must also be undecorated | documented constraint, not a bug | **[reported]** — [Kotlin docs](https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html) |
| Stays above **native Wayland** windows (not just XWayland ones) | Mutter keeps one stacking order across both, so ABOVE should win | **[inferred]** — see Open questions |
| Stays above fullscreen apps (video, games) | almost certainly not | **[inferred]** |
| Native Wayland Java backend | not in 21; prototype only, JDK 25 base | **[reported]** — [Project Wakefield](https://wiki.openjdk.org/display/wakefield/Pure+Wayland+toolkit+prototype), [Phoronix](https://www.phoronix.com/news/Java-Native-Wayland-Progress) |

The verification was a throwaway AWT probe (undecorated + always-on-top +
alpha-0 background + elliptical shape), shown briefly and inspected with
`xprop`. It is not part of the module.

### Why the XWayland question is moot

Guides tell you to force XWayland with `GDK_BACKEND` or
`-Dawt.toolkit.name=XToolkit`. On JDK 21 there is nothing to force: the only
toolkit is `XToolkit`, confirmed above. Java is an X11 client here whether you
ask for it or not, and that is precisely why `_NET_WM_STATE_ABOVE` works —
Mutter is a full X11 WM for XWayland clients.

This flips when native Wayland lands (Wakefield, JDK 25+). Wayland has no
protocol for a normal client to raise itself; a pet would then need
`wlr-layer-shell` (not implemented by Mutter) or a Shell extension. **A future
JDK upgrade is the thing most likely to break this**, not a GNOME update.

One unrelated XWayland footgun worth knowing: some AWT apps need
`_JAVA_AWT_WM_NONREPARENTING=1` under non-reparenting WMs. Not needed under
Mutter, but it is the first thing to try if the window misbehaves elsewhere.

---

## Recommended approach

Order matters — do step 1 before committing to any of the rest.

**1. Spike the risky window first.** Smallest possible Compose desktop app,
transparent + undecorated, drawing anything. If it throws the Skiko error,
you have learned the important thing for the cost of ten minutes.

```kotlin
// Illustrative only — belongs in a desktop demo app, not in :mascot.
fun main() = application {
    val state = rememberWindowState(
        width = 160.dp, height = 160.dp,
        position = WindowPosition(Alignment.BottomEnd),
    )
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        undecorated = true,      // required for transparency
        transparent = true,      // ← the risky line; see Skiko issues above
        alwaysOnTop = true,      // maps to _NET_WM_STATE_ABOVE via XWayland
        resizable = false,
        focusable = false,       // a pet should not steal focus / keyboard
        title = "mascot",
    ) {
        // MaterialBot(...) — the existing renderer, unchanged.
        // Then restrict input to the silhouette so clicks pass through
        // everywhere else:
        //   window.shape = Ellipse2D.Double(0.0, 0.0, w, h)
        // window is the ComposeWindow (a java.awt.Window), so setShape applies.
    }
}
```

Notes on that snippet:

- `alwaysOnTop` is a first-class `Window` parameter in Compose Desktop; it
  reaches `java.awt.Window.setAlwaysOnTop` underneath, which is the call
  verified working here.
- `focusable = false` matters. A pet that takes keyboard focus when clicked is
  an irritant, and on some WMs a focusable always-on-top window fights the
  window you are actually typing into.
- `setShape` is the pragmatic click-through. There is no compositor-level input
  region available to a JVM client here; shaping the window is the whole
  mechanism, and it is per-pixel-coarse (a path, not an alpha mask). Match it
  to the bot's silhouette, not its bounding box.
- Keep it out of the taskbar/alt-tab. AWT has no portable hint for this; if it
  shows up in the dock, that is a cosmetic bug to solve later (an `X11` window
  type hint via reflection, or accept it).

**2. Then wire the renderer.** The engine, config and `ui/MaterialBot.kt` are
already free of Android APIs apart from a small `android.graphics.Matrix`
usage, so they move to `commonMain` largely untouched. The sensor/vibrator code
(`live/ShakeDizzy.kt`) is Android-only by nature and stays in `androidMain` —
there is no accelerometer to shake and no vibrator to buzz back.

**3. Then give it something to react to.** See below.

---

## Fallbacks

If step 1 fails (Skiko refuses the transparent context):

1. **`-Dskiko.renderApi=SOFTWARE`** — forces Skiko off OpenGL. Commonly cited
   for exactly this class of Linux failure. **[reported, untested here]**.
   Costs GPU acceleration; for a 160×160 bot that is likely irrelevant.
2. **Opaque window, themed background.** Drop `transparent = true`, paint a
   solid rounded panel matching the desktop theme. Loses the "floating on the
   desktop" illusion but keeps everything else, and sidesteps the bug entirely.
3. **Swing/AWT host, Compose content.** The AWT path is verified working here;
   embedding a Compose panel in a hand-made transparent `JWindow` may dodge the
   Compose window wrapper. More moving parts — only if 1 and 2 both fail.
4. **Tray icon instead of a pet.** Least fun, most robust, near-zero window
   management risk.

If always-on-top itself ever stops working (i.e. after a JDK upgrade brings a
native Wayland backend):

- **GNOME Shell extension (GJS).** Real compositor access, correct layering,
  the genuinely native answer. The cost is severe and worth stating plainly:
  **the bot's renderer would have to be rewritten in JS/Clutter.** The whole
  appeal of the Compose route is that `MaterialBot.kt` runs verbatim. Do not
  take this path to fix a cosmetic stacking issue.
- **`gtk4-layer-shell`** is the correct protocol for desktop widgets, but it is
  a GTK/C library with no practical JVM binding, and Mutter does not implement
  `wlr-layer-shell` anyway. Not an option on GNOME.

---

## Desktop status inputs

The Android bot reacts to app state via the status bus. The desktop equivalent
wants cheap, poll-friendly sources. All confirmed present on this machine:

| Signal | Source | Note |
|---|---|---|
| CPU load | `/proc/loadavg` | field 1 = 1-min avg; divide by `nproc` (12 here) for a 0..1 busy-ness |
| Battery % | `/sys/class/power_supply/BAT0/capacity` | plain integer |
| Charging | `/sys/class/power_supply/ADP0/online` | `1` = on AC |
| Notifications / media / DND | D-Bus via `gdbus` or `busctl` | both installed |
| Build status | whatever you already run | e.g. tail a Gradle log, or a file the build touches |

A poll every few seconds is plenty — this drives a mood, not a gauge. Map to
existing `BotState`s rather than inventing new ones: high load → `Working`,
low battery → `Sleepy`, build failed → `Alert`.

---

## Open questions

Things that need a real run to settle; none are blockers for starting.

1. **Does `transparent = true` actually work on this Compose/Skiko version?**
   The single most important unknown. The upstream reports are from Compose
   1.5.x on Arch; whether current CMP on Ubuntu 24.04 still fails is unverified.
   Resolve with the step-1 spike.
2. **Does an ABOVE XWayland window really float over native Wayland windows?**
   Verified only that Mutter sets the ABOVE state. Not verified against a
   native-Wayland (GTK4) window, and I had no way to inspect cross-boundary
   stacking programmatically. Test by putting the pet next to a GTK4 app.
3. **Fullscreen behaviour.** Expect the pet to be covered by fullscreen video
   and games. Probably desirable; confirm it is not *permanently* buried after
   the fullscreen app exits.
4. **HiDPI / fractional scaling.** GNOME fractional scaling under XWayland is
   historically blurry. A 160dp bot may render soft. Untested.
5. **Multi-monitor.** Which output does `WindowPosition(Alignment.BottomEnd)`
   choose, and does the pet survive hotplug? Untested.
6. **Taskbar/alt-tab presence.** Unknown whether the window appears in the dock;
   cosmetic, but a pet in alt-tab is wrong.
