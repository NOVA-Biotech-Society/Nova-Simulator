You are helping me build a **JavaFX 3D simulator for a leg exoskeleton**.
Use Java (latest LTS) and JavaFX 3D. Priorities: accurate physics, clean modular design, and extensibility.

=== HIGH-LEVEL GOAL ===
Create a desktop JavaFX app that lets me:
1. Build and modify a **3D leg exoskeleton** model (hip → knee → ankle → foot).
2. Attach and configure **sensors** (IMU: accelerometer + gyroscope + magnetometer) and **motors** at different joints and segment locations.
3. Simulate the **movement of prostration** (full flexion sequence of hip, knee, and ankle) for a **dummy human** model wearing the exoskeleton.
4. Adjust **human body parameters** (height, segment lengths, mass, mass distribution) and **exoskeleton parameters** (size, weight, power, sensor positions, motor specs).
5. Run accurate-enough physics to:
    - Compute torques, joint angles, angular velocities, forces, and contact forces.
    - Flag movements that could be **dangerous** for the human (e.g., excessive torque, joint angles beyond safe limits, too-fast angular velocities).
6. Provide:
    - A **Play / Pause / Reset** control to run the simulation.
    - **Real-time statistics** and plots of sensor readings and motor commands during simulation.
    - A **history log** that can be inspected after the simulation (time series of sensor & motor data).
7. Allow a future module where an **AI/controller** can decide motor inputs over time; I should later be able to:
    - Plug in a control algorithm (e.g., PID, RL agent, etc.).
    - Run episodes and tune the control to get smooth movement.
    - Export the final “sensor → motor command” logic.

=== ARCHITECTURE & DESIGN REQUIREMENTS ===
Design the code in a modular, extensible way:

- Use a clear package structure, for example:
    - `simulation.physics` – physical models, integrators, constraints.
    - `simulation.model` – data structures for human, exoskeleton, joints, sensors, motors.
    - `simulation.controller` – control layer and (later) AI controller interfaces.
    - `simulation.view` – JavaFX 3D views, camera controls, UI panels.
    - `simulation.app` – application entry point, wiring, configuration.

- Use **MVC or MVVM-style separation**:
    - **Model**: pure simulation state & physics.
    - **View**: JavaFX 3D visualization + control panels.
    - **Controller/ViewModel**: links UI actions to simulation state.

- Provide configuration objects or builder patterns to:
    - Define human body parameters (mass, segment lengths, etc.).
    - Define exoskeleton parameters (segment geometry, weight, motor capabilities, sensor locations).
    - Define simulation parameters (time step, gravity, friction coefficients).

- Physics should be **reasonably realistic**:
    - Represent each leg segment (thigh, shank, foot) as rigid bodies with mass and inertia.
    - Represent joints (hip, knee, ankle) with joint limits, stiffness/damping where relevant.
    - Use a **time-stepped simulation** (e.g. semi-implicit Euler or better) to integrate motion.
    - Encode safe ranges:
        - Joint angle ranges (per joint).
        - Maximum allowed torque/force.
        - Maximum allowed angular velocity.
    - Implement a **safety evaluation** module that, at each time step:
        - Checks if any joint is outside a safe range.
        - Checks if torques/forces exceed thresholds.
        - Marks the timestep (and maybe segment/joint) as “dangerous” and exposes this to the UI.

- The **sensor model**:
    - IMU → derived from rigid body state:
        - Accelerometer: linear acceleration in local frame.
        - Gyroscope: angular velocity in local frame.
        - Magnetometer: approximate fixed global field projected into local frame.
    - Sensor objects must:
        - Be attachable to any segment at a specific local offset.
        - Produce time series output as the simulation runs.
        - Expose current value and history for logging and plotting.

- The **motor model**:
    - Attach motors to joints (hip, knee, ankle) or segments.
    - Motors have:
        - Max torque / power.
        - Control input (e.g. desired torque or angle setpoint).
    - Simulation uses motor commands to apply torques at joints each timestep.

- The **controller/AI integration point**:
    - Define interfaces like:
        - `interface ExoController { void reset(); MotorCommands computeCommands(SensorSnapshot sensors, ExoState state, double t); }`
    - Start with a simple scripted controller for the prostration motion (e.g., time-based target angles).
    - Keep this part modular so I can later plug in a more advanced AI or RL controller without rewriting the core simulation.

=== UI & INTERACTION REQUIREMENTS ===
Use JavaFX with 3D support:

- Main window layout:
    - Center: **3D viewport** displaying:
        - Human leg (or full body later).
        - Exoskeleton model attached to the leg.
    - Right or bottom: **control & data panels**, including:
        - Simulation controls: Play, Pause, Step, Reset, Simulation speed.
        - Human parameter controls: sliders/inputs for height, weight, segment lengths (basic version).
        - Exoskeleton parameter controls: segment lengths, thickness, weight, motor/sensor placement options.
        - Safety indicators: warnings about dangerous movements (per joint).
    - A **tabbed panel** or expandable sections for:
        - Real-time numeric readouts (sensor/motor values).
        - Charts for selected variables (e.g. joint angle over time, motor torque over time, etc.).
        - History log view.

- 3D camera controls:
    - Orbit around the model (mouse drag).
    - Zoom in/out (scroll).
    - Pan (e.g., shift + drag).
    - Ability to reset camera to default useful views (e.g., side view of the leg).

- Simulation control:
    - A “Prostration” motion scenario:
        - Pressing “Play” runs the simulation from initial standing pose through a prostration movement trajectory.
        - Allow specifying duration or speed factor.
    - Display current simulation time.
    - Highlight dangerous frames:
        - Change color of joints/segments if they are in a dangerous range.
        - Show warnings in the UI (e.g., a list of events: time, joint, type of violation).

- Data visualization:
    - Use JavaFX charts or custom plots:
        - Select which signals to plot (joint angle, torque, sensor readings, etc.).
        - Show them updating in real-time during simulation.
    - Keep a history buffer that can be inspected after the run:
        - Time series of key variables.
        - Ability to pause and inspect values at a given time.

=== IMPLEMENTATION DETAILS ===
- Start with a **single leg (hip–knee–ankle–foot)** and a simplified yet extensible 3D model.
- Use simple primitive 3D shapes (boxes, cylinders, etc.) to represent:
    - Human segments.
    - Exoskeleton frame and joints.
- Ensure a clean mapping from **simulation model** (positions, orientations) to **JavaFX 3D nodes** (transforms).
- Define a robust **time-step loop**:
    - Use a fixed or semi-fixed timestep.
    - Update simulation state.
    - Compute sensor outputs.
    - Compute safety metrics.
    - Update UI & 3D transforms appropriately (respecting JavaFX threading rules – simulation may run off the UI thread, UI updates on the JavaFX Application Thread).

- Provide clear, documented classes for:
    - `RigidBodySegment`
    - `Joint` (with limits, etc.)
    - `ExoskeletonSegment`
    - `Motor`
    - `Sensor` / `ImuSensor`
    - `HumanModel` and `ExoskeletonModel`
    - `SimulationState` and `SimulationEngine`
    - `SafetyEvaluator`
    - `ExoController` interface and at least one simple implementation
    - `MainApp` / `MainView` or similar for JavaFX

- Emphasize **readability and extensibility**:
    - Use interfaces and abstract classes where appropriate.
    - Add Javadoc or comments explaining the rationale for physics equations and modeling choices.
    - Avoid hardcoding parameters: use configuration objects or constants grouped in config classes.

=== WHAT TO GENERATE NOW ===
1. Set up the basic project structure for a JavaFX app with the above package layout.
2. Implement the core domain classes and interfaces (even if some methods are stubs at first).
3. Implement a minimal working simulation loop with:
    - A simple leg model (rigid segments + joints).
    - Basic gravity and joint constraints.
    - A very simple time-based controller that moves the leg through a prostration-like movement.
4. Implement a JavaFX 3D scene that:
    - Visualizes the leg + exoskeleton skeleton.
    - Allows free camera movement (orbit, zoom).
5. Add a basic UI with Play/Pause/Reset and a panel showing some real-time values (e.g. joint angles, one sensor value, one motor torque).
6. Comment the code clearly so I can later extend:
    - Sensor and motor models.
    - Safety checks.
    - AI control logic.
    - Human & exoskeleton configuration.

Write idiomatic, modern Java code and keep everything well structured so I can iteratively refine physics accuracy and AI later.