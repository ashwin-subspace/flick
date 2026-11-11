# Flick

Draw an animation's motion with your thumb. Get the code.

Every easing tool works backwards: it hands you two control handles on a graph and asks
you to guess numbers until it feels right. Flick inverts it — you perform the feeling,
and it reverse-engineers the math.

Fully offline. No accounts, no network, no third-party libraries, no runtime permissions.

## How it works

**Capture.** Drag across the canvas however the motion should feel. Three things make
this work: it reads `getHistorical*` touch samples (Android batches touch input, so most
of the digitizer's 120–240Hz resolution is invisible if you only read `getX()`); it
smooths with a One Euro filter, which cleans up hesitation without dulling a dash; and
it measures progress by projecting onto the start→end line, so a pull-back reads as
*negative* progress and anticipation becomes detectable.

**Fit.** Three models compete:

| Model | Params | Fitted with |
|---|---|---|
| Cubic bezier | 4 | Nelder-Mead, 6 starts, Newton solve to invert `x(t)` |
| Spring | 2 | damped harmonic step response, fits ζ and ω |
| Path | 2 per knot | cubic Hermite spline, knots at velocity extrema |

The path model is a real chain of cubics, not sampled stops. Knots land on the motion's
velocity extrema — the moments it changed its mind — and the spline converts exactly to
bezier segments, which is what Android's `PathInterpolator` actually wants. x is
strictly increasing by construction, so the result is always a valid easing function.

Selection is rule-based, not an information criterion — a many-knot spline nearly always
wins on raw error while being the least useful answer. A bezier is preferred whenever a
bezier is good enough.

**Refine.** The flick is a starting point, not a verdict: drag the two control points,
or snap them to 0.05 steps, mirror them into symmetry, clamp overshoot back inside 0–1,
and nudge the duration in 10ms steps.

**Read.** It names what it found in animator's language: anticipation depth and timing,
overshoot and settle time, whether the motion is front- or back-loaded.

**Feel.** Hold *Feel* and the curve plays as vibration — amplitude follows velocity.

**Export.** CSS, CSS `linear()`, Compose (`CubicBezierEasing` or real `spring()`),
Android `<pathInterpolator>`, Flutter, SwiftUI, and After Effects keyframe *influence*
percentages, which is the conversion nobody has in their pocket.

**Library.** Save curves with names and tags; search across name, tag or model; star
favourites to the top. The grid of thumbnails becomes your motion vocabulary. Export
the whole library as one `Motion.kt`, with duplicate names disambiguated.

## Build

No Gradle. `build.sh` drives the SDK tools directly:

```bash
bash build.sh
```

`aapt2 compile` → `aapt2 link` → `javac` → `d8` → `aapt add` → `zipalign` → `apksigner`.
Produces a signed `Flick-debug.apk` in about two seconds.

Requires a JDK, `build-tools/36.0.0`, `platforms/android-36`, and a debug keystore at
`~/.android/debug.keystore`. The script uses `JAVA_HOME` and `ANDROID_HOME` when they
point at a real install, and otherwise falls back to this machine's Android Studio
JBR and SDK.

## Tests

The `com.flick.math` package has no Android imports, so it runs on a desktop JVM:

```bash
javac -d build/test src/com/flick/math/*.java src/com/flick/SavedCurve.java \
      src/com/flick/LibraryExport.java test/TestMath.java test/TestApp.java
java -cp build/test TestMath   # 228 assertions
java -cp build/test TestApp    # 61 assertions
```

`TestMath` generates points from a *known* bezier and spring, then asserts the fitter
recovers the original parameters — that is how you know the math is real and not vibes.
`TestApp` covers persistence encoding and the Kotlin export.

## Layout

```
src/com/flick/math/   pure math, no Android — fitting, easings, exporters
src/com/flick/ui/     custom Views — capture, curve plot, preview wall, thumbnails
src/com/flick/        activity, SQLite store, haptics
test/                 desktop test harness
```

## Not done yet

- Landscape reflows but has no landscape-specific layout
- After Effects influence is approximated when the chosen model is not a bezier
- Haptics are untested on real hardware (an emulator reports a vibrator but has none)
