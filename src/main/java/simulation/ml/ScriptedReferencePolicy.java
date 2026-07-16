package simulation.ml;

import simulation.controller.MotorCommands;

/**
 * Reference policy that mirrors the existing scripted prostration trajectory.
 * <p>
 * This gives the Tribuo pipeline a safe fallback before a trained model is wired in.
 * </p>
 */
public class ScriptedReferencePolicy implements SimulationCommandPolicy {

    private static final double T_STAND_START = 0.0;
    private static final double T_DESCEND_START = 1.0;
    private static final double T_PROSTRATE = 3.0;
    private static final double T_RISE_START = 5.0;
    private static final double T_RISE_END = 7.0;
    private static final double T_CYCLE_END = 8.0;

    private static final double HIP_PROSTRATE = Math.toRadians(90);
    private static final double KNEE_PROSTRATE = Math.toRadians(120);
    private static final double ANKLE_PROSTRATE = Math.toRadians(20);

    private static final double HIP_STANDING = 0.0;
    private static final double KNEE_STANDING = 0.0;
    private static final double ANKLE_STANDING = 0.0;

    private double kpHip = 80.0;
    private double kdHip = 15.0;
    private double kpKnee = 60.0;
    private double kdKnee = 12.0;
    private double kpAnkle = 40.0;
    private double kdAnkle = 8.0;

    @Override
    public MotorCommands computeCommands(SimulationFeatureVector features) {
        double time = features.get("time_seconds");

        double hipTarget = computeTarget(time, HIP_STANDING, HIP_PROSTRATE);
        double kneeTarget = computeTarget(time, KNEE_STANDING, KNEE_PROSTRATE);
        double ankleTarget = computeTarget(time, ANKLE_STANDING, ANKLE_PROSTRATE);

        double hipAngle = features.get("hip_angle_rad");
        double hipOmega = features.get("hip_angular_velocity_rad_per_s");
        double kneeAngle = features.get("knee_angle_rad");
        double kneeOmega = features.get("knee_angular_velocity_rad_per_s");
        double ankleAngle = features.get("ankle_angle_rad");
        double ankleOmega = features.get("ankle_angular_velocity_rad_per_s");

        double hipTorque = kpHip * (hipTarget - hipAngle) + kdHip * (0 - hipOmega);
        double kneeTorque = kpKnee * (kneeTarget - kneeAngle) + kdKnee * (0 - kneeOmega);
        double ankleTorque = kpAnkle * (ankleTarget - ankleAngle) + kdAnkle * (0 - ankleOmega);

        return new MotorCommands(hipTorque, kneeTorque, ankleTorque);
    }

    @Override
    public String getName() {
        return "Scripted Reference Policy";
    }

    public void setHipGains(double kp, double kd) {
        this.kpHip = kp;
        this.kdHip = kd;
    }

    public void setKneeGains(double kp, double kd) {
        this.kpKnee = kp;
        this.kdKnee = kd;
    }

    public void setAnkleGains(double kp, double kd) {
        this.kpAnkle = kp;
        this.kdAnkle = kd;
    }

    private double computeTarget(double time, double standingAngle, double prostrateAngle) {
        double t = time % T_CYCLE_END;

        if (t < T_STAND_START + 1.0) {
            return standingAngle;
        } else if (t < T_PROSTRATE) {
            double alpha = smoothStep((t - T_DESCEND_START) / (T_PROSTRATE - T_DESCEND_START));
            return standingAngle + alpha * (prostrateAngle - standingAngle);
        } else if (t < T_RISE_START) {
            return prostrateAngle;
        } else if (t < T_RISE_END) {
            double alpha = smoothStep((t - T_RISE_START) / (T_RISE_END - T_RISE_START));
            return prostrateAngle + alpha * (standingAngle - prostrateAngle);
        }
        return standingAngle;
    }

    private double smoothStep(double t) {
        t = Math.max(0, Math.min(1, t));
        return 0.5 * (1.0 - Math.cos(Math.PI * t));
    }
}

