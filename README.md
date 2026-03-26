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

---

##  Project Evolution (Demo Videos)

| Date | Description | Link |
| :--- | :--- | :--- |
| **2026-03-25** | **Latest: Constraint Stabilization & UI Overlay** | [Watch on Vimeo](https://vimeo.com/1177131268?share=copy&fl=cl&fe=ci) |
| **2026-03-04** | **Initial: 3D Rendering & Basic Physics** | [Watch on Vimeo](https://vimeo.com/1177130383?share=copy&fl=cl&fe=ci) |

---

##  Tech Stack
* **Language:** Java 21
* **Graphics:** JavaFX 3D
* **Build Tool:** Maven

---

## Constributors
* Belhaddad Ilyes
* Mohamed Elyes Bradai

---

Developed for Nova Biotech Society
