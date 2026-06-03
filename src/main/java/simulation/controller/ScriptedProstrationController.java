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
    private double kpHip   = 50.0;
    private double kdHip   = 20.0;
    private double kpKnee  = 45.0;
    private double kdKnee  = 15.0;
    private double kpAnkle = 45.0;
    private double kdAnkle = 5.0;

    // Keyframe times (seconds)
    private static final double T_STAND_START  = 0.0;
    private static final double T_DESCEND_START = 1.0;
    private static final double T_PROSTRATE    = 3.0;
    private static final double T_RISE_START   = 5.0;
    private static final double T_RISE_END     = 7.0;
    private static final double T_CYCLE_END    = 8.0;

    // Target angles (radians) for prostration pose
    private static final double HIP_PROSTRATE   = Math.toRadians(-28);
    private static final double KNEE_PROSTRATE   = Math.toRadians(110);
    private static final double ANKLE_PROSTRATE  = Math.toRadians(-40);

    // Standing pose (all near zero)
    private static final double HIP_STANDING   = Math.toRadians(0);
    private static final double KNEE_STANDING  = Math.toRadians(0);
    private static final double ANKLE_STANDING = Math.toRadians(0);

    // History of angles to estimate omega (angular velocity)
    private double lastHipAngle = 0.0;
    private double lastKneeAngle = 0.0;
    private double lastAnkleAngle = 0.0;

    // History of torques for the low-pass filters
    private double lastHipTorque = 0.0;
    private double lastKneeTorque = 0.0;
    private double lastAnkleTorque = 0.0;

    @Override
    public void reset() {
        // No internal state to reset for the scripted controller
        this.lastAnkleTorque = 0.0;
    }

    @Override
    public MotorCommands computeCommands(SimulationState state, double time) {
        double dt = state.getDt();

        // 1. Compute target angles based on the current phase
        double hipTarget   = computeTarget(time, HIP_STANDING, HIP_PROSTRATE);
        double kneeTarget  = computeTarget(time, KNEE_STANDING, KNEE_PROSTRATE);
        double ankleTarget = computeTarget(time, ANKLE_STANDING, ANKLE_PROSTRATE);

        // 2. Get current joint states and angles
        Joint knee  = state.getHumanModel().getKneeJoint();
        Joint ankle = state.getHumanModel().getAnkleJoint();

        double currentHipAngle   = state.getHumanModel().getThigh().getAngle();
        double currentKneeAngle  = knee.getAngle();
        double currentAnkleAngle = ankle.getAngle();

        // 3. Compute normalized angle errors (prevents 360-degree spasms)
        double hipError   = normalizeAngle(hipTarget - currentHipAngle);
        double kneeError  = normalizeAngle(kneeTarget - currentKneeAngle);
        double ankleError = normalizeAngle(ankleTarget - currentAnkleAngle);

        // 4. Clean estimation of angular velocities (bypasses ground constraint noise)
        double estimatedHipOmega   = (currentHipAngle - this.lastHipAngle) / dt;
        double estimatedKneeOmega  = (currentKneeAngle - this.lastKneeAngle) / dt;
        double estimatedAnkleOmega = (currentAnkleAngle - this.lastAnkleAngle) / dt;

        // Save current angles for the next frame
        this.lastHipAngle   = currentHipAngle;
        this.lastKneeAngle  = currentKneeAngle;
        this.lastAnkleAngle = currentAnkleAngle;

        // 5. Compute raw PD torques using the estimated velocities
        double rawHipTorque   = kpHip * hipError + kdHip * (0.0 - estimatedHipOmega);
        double rawKneeTorque  = kpKnee * kneeError + kdKnee * (0.0 - estimatedKneeOmega);
        double rawAnkleTorque = kpAnkle * ankleError + kdAnkle * (0.0 - estimatedAnkleOmega);

        // 6. Apply low-pass filter to ALL torques (smoothes out transitions)
        double smoothingFactor = 0.15;
        double hipTorque   = lastHipTorque + smoothingFactor * (rawHipTorque - lastHipTorque);
        double kneeTorque  = lastKneeTorque + smoothingFactor * (rawKneeTorque - lastKneeTorque);
        double ankleTorque = lastAnkleTorque + smoothingFactor * (rawAnkleTorque - lastAnkleTorque);

        // Save filtered torques for the next frame
        this.lastHipTorque   = hipTorque;
        this.lastKneeTorque  = kneeTorque;
        this.lastAnkleTorque = ankleTorque;

        // 7. Safety clamping (prevents extreme torque spikes to protect the model)
        double MAX_TORQUE = 60.0;
        double MAX_ANKLE_TORQUE = 20.0; // Kept lower to let the foot naturally adapt to the floor

        hipTorque   = Math.max(-MAX_TORQUE, Math.min(MAX_TORQUE, hipTorque));
        kneeTorque  = Math.max(-MAX_TORQUE, Math.min(MAX_TORQUE, kneeTorque));
        ankleTorque = Math.max(-MAX_ANKLE_TORQUE, Math.min(MAX_ANKLE_TORQUE, ankleTorque));

        return new MotorCommands(hipTorque, kneeTorque, ankleTorque);
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI)  angle -= 2.0 * Math.PI;
        while (angle < -Math.PI) angle += 2.0 * Math.PI;
        return angle;
    }

    /**
     * Computes the target angle at the given time using smooth interpolation between keyframes.
     * Uses cosine interpolation for smooth acceleration/deceleration.
     */
    private double computeTarget(double time, double standingAngle, double prostrateAngle) {
        // Wrap time into the cycle
        double t = time % T_CYCLE_END;

//        System.out.println(standingAngle);
//        System.out.println(prostrateAngle);
//        System.out.println("---");


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
//        System.out.println( 0.5 * (1.0 - Math.cos(Math.PI * t)));
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
