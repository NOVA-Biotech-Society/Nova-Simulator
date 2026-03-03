package simulation.controller;

import simulation.model.Joint;
import simulation.model.SimulationState;

/**
 * A scripted controller that moves the leg through a prostration (sujood) motion.
 * <p>
 * The motion sequence is defined by time-based keyframes for target joint angles.
 * A PD controller tracks the trajectory at each joint:
 * </p>
 * <pre>
 *     torque = Kp * (targetAngle - currentAngle) + Kd * (0 - angularVelocity)
 * </pre>
 *
 * <h3>Motion phases:</h3>
 * <ol>
 *   <li><b>Standing</b> (0–1s): upright pose, all joints near 0°.</li>
 *   <li><b>Descending</b> (1–3s): flex hip and knee to lower the body.</li>
 *   <li><b>Prostration</b> (3–5s): hold deep flexion (hip ~90°, knee ~130°, ankle ~20°).</li>
 *   <li><b>Rising</b> (5–7s): extend back to standing.</li>
 *   <li><b>Standing</b> (7–8s): hold standing pose.</li>
 * </ol>
 */
public class ScriptedProstrationController implements ExoController {

    // PD gains
    private double kpHip   = 80.0;
    private double kdHip   = 15.0;
    private double kpKnee  = 60.0;
    private double kdKnee  = 12.0;
    private double kpAnkle = 40.0;
    private double kdAnkle = 8.0;

    // Keyframe times (seconds)
    private static final double T_STAND_START  = 0.0;
    private static final double T_DESCEND_START = 1.0;
    private static final double T_PROSTRATE    = 3.0;
    private static final double T_RISE_START   = 5.0;
    private static final double T_RISE_END     = 7.0;
    private static final double T_CYCLE_END    = 8.0;

    // Target angles (radians) for prostration pose
    private static final double HIP_PROSTRATE   = Math.toRadians(90);
    private static final double KNEE_PROSTRATE   = Math.toRadians(120);
    private static final double ANKLE_PROSTRATE  = Math.toRadians(20);

    // Standing pose (all near zero)
    private static final double HIP_STANDING   = Math.toRadians(0);
    private static final double KNEE_STANDING  = Math.toRadians(0);
    private static final double ANKLE_STANDING = Math.toRadians(0);

    @Override
    public void reset() {
        // No internal state to reset for the scripted controller
    }

    @Override
    public MotorCommands computeCommands(SimulationState state, double time) {
        // Compute target angles based on the current phase
        double hipTarget   = computeTarget(time, HIP_STANDING, HIP_PROSTRATE);
        double kneeTarget  = computeTarget(time, KNEE_STANDING, KNEE_PROSTRATE);
        double ankleTarget = computeTarget(time, ANKLE_STANDING, ANKLE_PROSTRATE);

        // Get current joint states
        Joint knee  = state.getHumanModel().getKneeJoint();
        Joint ankle = state.getHumanModel().getAnkleJoint();

        // PD control: torque = Kp*(target - current) + Kd*(0 - omega)
        // For hip, the "angle" is the thigh's absolute angle
        double hipAngle = state.getHumanModel().getThigh().getAngle();
        double hipOmega = state.getHumanModel().getThigh().getAngularVelocity();
        double hipTorque = kpHip * (hipTarget - hipAngle) + kdHip * (0 - hipOmega);

        double kneeTorque = kpKnee * (kneeTarget - knee.getAngle()) + kdKnee * (0 - knee.getAngularVelocity());
        double ankleTorque = kpAnkle * (ankleTarget - ankle.getAngle()) + kdAnkle * (0 - ankle.getAngularVelocity());

        return new MotorCommands(hipTorque, kneeTorque, ankleTorque);
    }

    /**
     * Computes the target angle at the given time using smooth interpolation between keyframes.
     * Uses cosine interpolation for smooth acceleration/deceleration.
     */
    private double computeTarget(double time, double standingAngle, double prostrateAngle) {
        // Wrap time into the cycle
        double t = time % T_CYCLE_END;

        if (t < T_STAND_START + 1.0) {
            // Initial standing phase
            return standingAngle;
        } else if (t < T_PROSTRATE) {
            // Descending phase: interpolate from standing to prostration
            double alpha = smoothStep((t - T_DESCEND_START) / (T_PROSTRATE - T_DESCEND_START));
            return standingAngle + alpha * (prostrateAngle - standingAngle);
        } else if (t < T_RISE_START) {
            // Prostration hold
            return prostrateAngle;
        } else if (t < T_RISE_END) {
            // Rising phase: interpolate from prostration back to standing
            double alpha = smoothStep((t - T_RISE_START) / (T_RISE_END - T_RISE_START));
            return prostrateAngle + alpha * (standingAngle - prostrateAngle);
        } else {
            // Final standing hold
            return standingAngle;
        }
    }

    /**
     * Smooth step function using cosine interpolation.
     * Maps [0, 1] → [0, 1] with zero velocity at endpoints.
     */
    private double smoothStep(double t) {
        t = Math.max(0, Math.min(1, t));
        return 0.5 * (1.0 - Math.cos(Math.PI * t));
    }

    @Override
    public String getName() {
        return "Scripted Prostration Controller";
    }

    // ---- PD gain setters for tuning ----

    public void setHipGains(double kp, double kd) { this.kpHip = kp; this.kdHip = kd; }
    public void setKneeGains(double kp, double kd) { this.kpKnee = kp; this.kdKnee = kd; }
    public void setAnkleGains(double kp, double kd) { this.kpAnkle = kp; this.kdAnkle = kd; }
}
