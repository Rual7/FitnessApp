# FitnessApp

Android app (Kotlin, Jetpack Compose) for bodyweight training with real-time exercise analysis using
on-device pose estimation (MediaPipe Pose Landmarker). Bachelor thesis project.

**Current state: prototype.** One exercise (bicep curl) is used to test the camera, the landmarks, repetition
counting and out-of-frame handling. The video never leaves the device and is never stored.
Project context and decisions live in the separate `Lucrare-de-Licenta` documentation folder.

## What the prototype does

- Home screen with one button per exercise (for now: bicep curl).
- Camera preview with the body skeleton drawn over it (no face).
- Counts repetitions from the elbow angle; incomplete repetitions are not counted.
- Tracking states: searching, positioning, ready (3 s countdown), counting, out of frame. Leaving the frame pauses
  counting and drops the repetition in progress; the absence is timed.
- Debug panel with camera and pose FPS, inference latency, elbow angle, state and landmark visibility.
- Finish button: ends the set and opens the session summary (repetitions, duration, active time, time and number of
  exits out of frame, average pace per repetition, range of motion, average pose speed).
- Manual-count comparison: the user types the number they counted and the app shows the difference.
- Animated splash screen: the ring of the logo fills segment by segment.

Not in the prototype yet: saving sessions locally (Room), CSV export, landmark recording.

## Requirements

- Android Studio (it bundles the JDK) and the Android SDK, platform 37
- A device or an emulator with a camera (emulator: use the PC webcam as front camera)

## Run

Android Studio: `File > Open` the project folder, wait for the Gradle sync, pick a device and press Run.
The first time the exercise screen opens, grant the camera permission.

Command line:

```bash
./gradlew installDebug        # build and install on the running emulator or phone
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew lintDebug           # Android lint
```

Emulator with the PC webcam as front camera (or set `Camera Front: Webcam0` in the AVD settings):

```bash
emulator -avd Medium_Phone_API_37.0 -camera-front webcam0
```

Windows note: if Gradle fails with `Unable to establish loopback connection`, the JVM cannot create its internal
pipe because the temp path is an 8.3 short path. Point Java's Unix-domain socket directory to a short, existing
folder, for example `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\<you>\.gradle\jtmp`.

## Structure

```
app/src/main/java/com/fitnessapp/
├── MainActivity.kt          entry point: system splash, theme, navigation
├── navigation/              routes: splash -> exercise selection -> workout
├── ui/                      shared formatting and the Cream & black theme (light and dark)
├── feature/
│   ├── exercises/           exercise list and the home screen
│   ├── splash/              loading screen and the animated ring (RingSpec.kt is generated)
│   ├── summary/             session summary and the manual-count comparison
│   └── workout/             workout screen, overlays, repetition panel, view model
└── cv/                      computer vision
    ├── camera/              CameraX preview and frame analysis
    ├── pose/                PoseEngine interface, MediaPipe implementation, landmark types
    ├── metrics/             angle math, FPS meter
    ├── engine/              arm reading, repetition counter, bicep curl tracker, session summary
    └── tracking/            tracking state machine

app/src/test/                JVM unit tests (engine, tracking, metrics) on synthetic landmark sequences
app/src/main/assets/         pose_landmarker_lite.task (MediaPipe model)
design/                      logo source image, tracer and generator
```

`cv/engine`, `cv/tracking` and `cv/metrics` have no Android dependencies, so they run in plain JVM tests.
Every threshold in them is a placeholder starting value to be tuned on real recordings.

The model `pose_landmarker_lite.task` comes from
`https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task`.

## Logo

The logo is vectorized from the author's reference image (`design/reference/logo-reference.jpg`). Requires Python with Pillow:

```bash
python design/trace_logo.py      # reference image -> design/logo_trace.json (figure, check, joints)
python design/generate_logo.py   # -> design/logo.svg, launcher/splash vectors, feature/splash/RingSpec.kt
```

The ring (segments, dashes, radii, colors) is generated into `RingSpec.kt`, so the splash animation and the
static logo cannot drift apart. Do not edit the generated files by hand: `res/drawable/ic_launcher_*.xml`,
`ic_splash_icon.xml`, `ic_logo_figure.xml` and `RingSpec.kt`.
