# MediaPipe motion capture

The optional capture workspace adds a device camera, matching skeleton overlay,
hip/knee/ankle observations, live traces, a cyan NOVA comparison avatar, standing
calibration, labelled recording, and offline replay to the main split view.

## One-time setup

Use **standard 64-bit Python 3.9–3.13**, a webcam, and the existing Java 21/Maven setup.
Python 3.13 ARM is supported on Apple Silicon and through the Windows ARM
compatibility path described below. Free-threaded Python builds are not supported.
In the app, open **Motion capture → Capture setup → Set up capture…**, then
choose **Install components**. This prepares the project capture environment,
installs the pinned packages, downloads the pose model, and checks JPEG encoding
and actual VIDEO-mode inference without accessing the camera.
Progress appears in the toolbar; **Cancel setup** stops the installer. Setup
does not open the camera. Once complete, click **Enable camera**, then
**Enable MediaPipe**. Existing Python 3.9–3.12 environments retain their verified
dependency versions; Python 3.13 uses MediaPipe 0.10.35, OpenCV 4.13 and NumPy 2.2.6.

### Python 3.13 on ARM

- **Apple Silicon (macOS 13+)**: camera and inference run natively in the project's
  `.venv` using ARM64 Python 3.13.
- **Windows 11 ARM64**: the ARM Python runs setup and launches a separate x64
  Python 3.13.15 worker under Windows emulation. OpenCV currently has no native
  Windows ARM64 wheel. The isolated worker lives in
  `.nova-capture/python-3.13.15-x64`; the installed ARM Python and existing `.venv`
  are preserved. The worker uses the same private process pipes and cleanup path.
  Performance must be measured on the actual device.

On Windows ARM, use the app's **Set up capture…** action, or run this with the
installed ARM Python from the repository root:

```powershell
python vision/setup_capture.py
mvn javafx:run
```

The compatibility runtime and pip bootstrap come from the official
[Python 3.13.15 release](https://www.python.org/downloads/release/python-31315/)
and [PyPA pip 25.2 wheel](https://pypi.org/project/pip/25.2/). Their SHA-256 values
are pinned and verified before extraction. Installation accepts binary packages
only and never attempts an OpenCV source build. A readiness marker is written
only after setup and inference succeed; updated requirements require setup again.
Downloads occur only through the explicit setup action. If an interrupted runtime
folder is reported, close NOVA, rename that specific folder, then retry setup.

Maintainers updating the embedded runtime must update its version, official URL
and release SHA-256 together in `capture_runtime.py`, then run the Windows ARM CI
job. Third-party runtime files are ignored by git and are not redistributed in
this repository.

### Manual setup on other supported systems

From the repository root create the environment:

```bash
python -m venv .venv
```

Windows PowerShell:

```powershell
.venv\Scripts\python.exe -m pip install -r vision/requirements.txt
.venv\Scripts\python.exe vision/setup_model.py
mvn javafx:run
```

macOS/Linux:

```bash
.venv/bin/python -m pip install -r vision/requirements.txt
.venv/bin/python vision/setup_model.py
mvn javafx:run
```

The setup script downloads Google's version-1 Pose Landmarker Lite model over
HTTPS and validates the task archive. Capture works offline after setup. The app
does not install packages or download models when the camera is enabled.

The launcher locates `vision` beside the compiled app or above its working
directory, so IDE launches from `target/classes` or another directory work.
It discovers the project's `.venv` automatically and also recognizes the Windows
`py -3.13` / `py -3.12` / `py -3.11` launchers and an active virtual environment. For other installations,
pass JVM properties `nova.vision.python` (the interpreter executable, without
shell quotes or flags) and `nova.vision.dir` (the absolute folder containing
`pose_service.py`) through the Java launcher/IDE. The fallback interpreter is
`python3` or `python` on PATH. An explicit interpreter override is authoritative;
install packages into that environment manually, or remove the override before
using the app's project-environment setup.

## Using the workspace

1. Click **Enable camera** in the main-view toolbar. In **Capture setup**, camera
   index 0 is the default. Stop the camera before selecting another index.
2. Click **Enable MediaPipe**. This also opens the camera if it is off. Camera-only
   preview remains available when MediaPipe is disabled or its model is missing.
3. Use a fixed side-on camera with the participant's shoulder, hip, knee, ankle,
   heel, and toes visible. Set **Visible side** and **Subject faces** according to
   the original camera image. **Mirror preview** mirrors the image and skeleton
   together; it does not change measurements or orientation settings.
4. Stand still and click **Calibrate standing pose**. Thirty consecutive, stable,
   visible standing samples establish neutral offsets. Occlusion or motion resets
   the sample count. A bent knee cannot be calibrated as straight.
5. Watch **RAW / POSE / SIM** values, live traces, and the cyan avatar. Capture also
   works while the simulation is paused. Selecting a **Keyframe label** compares
   the pose to that keyframe in the existing AI `ReferenceTrajectory`. Its bar is
   geometric similarity, not inferred movement phase: full at zero RMS error and
   empty at 45° RMS error.
6. Click **Record session**, choose a `.jsonl` file, and change the manual keyframe
   label during the sequence. **Stop & save session** finalizes the file. Turning
   off MediaPipe/camera or closing the app also finishes recording.
7. **Open replay…** stops the camera and replays the session at its recorded times.
   It shows recorded landmarks, angles, traces and the avatar. **Replay again**
   restarts a completed replay. Camera images are never retained.

**Motion capture** collapses the panel without stopping capture or recording. Use
the camera toggle to release the device. On tracking loss or a stream older than
500 ms, live avatar/angle values disappear and charts preserve gaps. The existing
simulation continues independently.

## Architecture and measurement conventions

`vision/pose_service.py` owns OpenCV and the MediaPipe Tasks Pose Landmarker.
VIDEO inference runs in this external Python worker. It cannot block JavaFX or
the physics loop. Each transmitted JPEG and skeleton comes from the same image.
Capture keeps the device's native format and bounds transport to 960×720 and
30 FPS. It validates the first real frame before announcing readiness. On Windows,
DirectShow is tried first, followed by Media Foundation if opening or initial
frame delivery fails; failed handles are released before retrying. A startup
watchdog stops a worker stuck in a native camera call after 40 seconds.

`PoseInputService` manages the child process on background threads. Private
stdin/stdout pipes carry versioned NDJSON; no listening port is opened. This is
the app-owned-process alternative to the report's local UDP/WebSocket suggestion.
Java retains only the latest preview and validates sizes, metadata, coordinates,
sequence IDs, timestamps and JPEG signatures. `PoseSource` and `PoseFrame` also
support `ReplayPoseSource` and future sensor providers.

`PoseAvatar` is an independent, mouse-transparent scene node. It never writes to
`HumanModel`, `SimulationState`, controllers, motors or safety evaluators. No
camera observation is sent to hardware or used as a torque command. Existing
default/AI/hardware controls, simulation CSV and sensor paths remain unchanged.

| Measurement | Convention |
| --- | --- |
| Hip | Absolute thigh tilt from downward vertical, as required by `HumanModel`. Positive X is opposite the subject's facing direction, matching NOVA's positive knee-flexion convention. Shoulder confidence confirms upper-body visibility; this is not trunk-relative hip flexion. |
| Knee | 180° minus the internal hip/knee/ankle angle; flexion positive, straight zero. |
| Ankle | 90° minus the angle between ankle→knee and heel→toe; dorsiflexion positive, perpendicular foot zero. |
| Coordinates | Aspect-correct image pixels for sagittal angles. Normalized and world landmarks are both preserved. |
| Confidence | Minimum visibility/presence across required points; threshold 0.6. Off-screen or degenerate limbs invalidate tracking. |
| Filtering | Exponential filter, 70 ms time constant; derived velocity bounded to ±12 rad/s. Gaps over 250 ms or invalid tracking reset history. |
| Limits | Mapped hip −30°…130°, knee 0°…140°, ankle −50°…30°. Raw observations remain available. |

The cyan avatar follows **existing NOVA segment algebra**: thigh = hip, shank =
hip + knee, foot = hip + knee + ankle. The existing model draws all segments
vertically at zero; the physical camera foot is perpendicular at ankle zero.
This compatibility mapping is not a clinical anatomical model. The image skeleton
shows the source geometry. The AI reference poses also differ from the current
scripted controller's keyframes; reference similarity uses `ReferenceTrajectory`.

## Recording and metrics

Each JSONL starts with a version-1 `session` header containing UUID, creation time,
radian units and purpose. Each subsequent `pose` line contains the session ID,
manual keyframe label, Java `receivedAtMs`, and a `PoseFrame` with:

- Capture timestamp, sequence ID and image dimensions.
- Raw and mapped angles, angular velocities, per-joint confidence and validity.
- Calibration flag, visible side and facing direction.
- All 33 normalized and 33 world landmarks when detected, including visibility
  and presence. Invalid observations retain `trackingValid: false`.

A bounded background queue writes a temporary `.partial` file. Successful flush
renames it to the selected destination. Slow/full storage stops recording with an
error and preserves the partial file; failure is not silently marked successful.
Replay streams with constant memory, checks chronology and session identity, and
stops on malformed input.

```bash
python vision/benchmark_session.py session.jsonl
python vision/benchmark_session.py session.jsonl --csv training.csv
python vision/benchmark_session.py stationary-30s.jsonl --static
```

The summary reports observed FPS, tracking loss and median capture-to-Java time.
`--static` computes raw knee RMS fluctuation about its mean; use it only for a
stationary recording. It does not measure absolute angle accuracy. UI **frame
age** additionally includes elapsed time since receipt to expose stalled streams.

## Validation

```bash
mvn test
python -m unittest discover -s vision/tests -v
```

`PoseMathChecks` also runs on a plain JDK without JavaFX/Gson/JUnit. It checks known
poses, angle signs, image aspect ratio, orientation, confidence, occlusion,
filtering, velocity resets and calibration. Maven includes session round-trip,
input-bound and failure tests alongside the original suite. CI checks Java 21 and
the pinned Python dependencies on Linux and Windows. Separate Python 3.13 jobs
exercise setup, JPEG codecs, actual VIDEO inference and 33-point normalized/world
landmarks on Linux x64, Windows x64, Apple Silicon and Windows ARM. Under Linux's virtual
display, the JavaFX regression runs repeated actual layout pulses with landscape
and portrait preview images, expands/collapses the panel, and resizes the window
down and up while asserting stable window and divider positions.

Before merging, verify these real-device scenarios:

- Default launch and original controls, dragging, CSV import/export, AI diagnostics
  and hardware mode. No Python or camera work should occur until requested.
- Camera-only → MediaPipe → camera-only → off; rapid toggles; close during startup,
  loading, tracking and recording. Confirm the device becomes available again.
- Missing Python/packages/model; occupied/denied/unplugged camera; full/partial
  occlusion and re-entry. Each must show a recoverable state.
- Layout at 900×600 and 1280×720, splitter resizing, keyboard focus and mirrored
  overlay alignment. Replay completion must not appear live.
- Five standing → descent → prostration → ascent → standing repetitions, labelled
  recording and replay. Separately record 30 seconds stationary; run capture for
  20 minutes; measure key-pose error against an independent reference.

The PDF targets (≥20 pose FPS, <100 ms median latency, <3° static knee RMS, <5%
tracking loss, <10° mean manual-reference error) are **acceptance targets**, not
measured claims. They require representative camera hardware and participants.
Loose clothing, occlusion and out-of-plane motion can reduce accuracy. This is an
R&D observation/dataset tool.

## Troubleshooting

- **Camera unavailable**: grant OS camera permission to the app/terminal/Python,
  close other camera apps, select another index and retry. On Windows, enable
  camera access for **desktop apps**, including the Java/Python process. A working
  Windows Camera app alone does not establish access for desktop programs.
- **Missing Python/OpenCV/packages**: install standard 64-bit Python 3.13 or 3.11, restart NOVA,
  then use **Set up capture…**. If automatic setup fails, use the manual commands
  above. The setup button does not install or upgrade system Python.
- **Unclear startup error**: use **Copy error details** in Capture setup. Native
  worker errors are retained in a bounded diagnostic message instead of being
  replaced with a generic disconnection message.
- **MediaPipe unavailable**: run setup using the interpreter the app uses. Python
  3.13 and 3.11 are supported; Windows ARM requires the compatibility setup.
  Camera-only mode still works.
- **Startup timeout / stream stalled**: stop the camera, check device and OS
  permission, and retry. Model initialization can temporarily delay frames.
- **Incorrect angle signs**: verify visible side and facing direction in the
  original image, then recalibrate. Keep the camera side-on and level.
- **Low confidence**: improve lighting, move back and keep feet visible. Invalid
  observations must be excluded when building training targets.

Sources: [Google's Python guide](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/python),
[model overview](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker),
and the supplied *Rapport de faisabilité et preuve de concept MediaPipe/NOVA*,
v1.0, 20 August 2026. MediaPipe is Apache-2.0 licensed; review model and dependency
terms when redistributing the optional runtime.
