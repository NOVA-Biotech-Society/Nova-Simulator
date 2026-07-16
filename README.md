# Nova-Simulator
> **The one and only AI training simulator for Nova's exoskeleton.**

This simulator is designed to bridge the gap between biomechanical physics and machine learning, providing a high-fidelity environment for testing the **Nova Biotech** exoskeleton.

---

##  Overview
The Nova-Simulator is a 2D physics engine (rendered in 3D) built with **JavaFX**. It allows for real-time simulation of human gait cycles, sensor data acquisition (IMU), and exoskeleton torque assistance.

### Key Features
* **Physics Engine:** Rigid body dynamics with semi-implicit Euler integration.
* **Kinematic Constraints:** Real-time joint stabilization (Hip, Knee, Ankle) using `enforcePositionConstraints`.
* **Modular Design:** Drag-and-drop system for placing motors and sensors on segments.
* **Data Export:** CSV logging for height, mass, and motor performance analysis.
* **ML-Ready Control Boundary:** Feature extraction and controller adapters are now isolated in `simulation.ml` so a Tribuo model can be wired in later without changing the engine.

---

##  Tribuo / ML Skeleton
The project now includes a clean ML seam for future TCN training and inference work:

* `simulation.ml.SimulationFeatureExtractor` turns a `SimulationState` into a stable feature vector.
* `simulation.ml.DefaultSimulationFeatureExtractor` captures the current simulator state in an ordered schema.
* `simulation.ml.FeatureBuffer` builds a rolling temporal window of IMU + joint-state samples for TCN input.
* `simulation.ml.ReferenceTrajectory` converts phase predictions into smooth joint targets.
* `simulation.controller.AIProstrationController` is the AI-assisted control path: temporal inference, PD control, and mandatory safety clamping.
* `simulation.ml.AIControlDiagnostics` and the new control panel section expose current keyframe, progression, confidence, and safety mode.
* `simulation.app.ControllerFactory` selects between scripted and AI-assisted control via `SimulationConfig.ControllerMode`.

When `ControllerMode.TRIBUO` is selected, the simulator now runs the AI controller skeleton with a temporal feature buffer, confidence-based fallback, and CSV telemetry logging at `logs/ai/nova-exo-telemetry.csv` by default.

To switch modes later, set the config controller mode to `TRIBUO` and point `SimulationConfig.tribuoModelPath` at your trained model artifact. The current baseline uses a heuristic temporal inference engine until you replace it with your trained TCN backend.

---

##  Project Evolution (Demo Videos)

| Date | Description | Link |
| :--- | :--- | :--- |
| **2026-04-09** | **Latest: 360 Mode, Showcase Mode, Movement Fix, NovaRemote integration** | [Coming Soon] |
| **2026-03-25** | **Constraint Stabilization & Import/Export Parameters** | [Watch on Vimeo](https://vimeo.com/1177131268?share=copy&fl=cl&fe=ci) |
| **2026-03-04** | **Initial: 3D Rendering & Basic Physics** | [Watch on Vimeo](https://vimeo.com/1177130383?share=copy&fl=cl&fe=ci) |

---

##  Tech Stack
* **Language:** Java 21
* **Graphics:** JavaFX 3D
* **Build Tool:** Maven
* **ML Library:** Tribuo (dependency staged for future training/inference work)

---

## Constributors
* Belhaddad Ilyes
* Mohamed Elyes Bradai

---

Developed for Nova Biotech Society
