package simulation.ml;

import simulation.controller.MotorCommands;
import simulation.model.Joint;
import simulation.model.Motor;
import simulation.model.SimulationState;

/**
 * Final safety layer that clamps torques and latches hold mode on violations.
 */
public class SafetyLimiter {

    private final double maxDeltaTorquePerSecond;
    private MotorCommands lastSafeCommands = MotorCommands.ZERO;
    private boolean latchedHold;

    public SafetyLimiter(double maxDeltaTorquePerSecond) {
        this.maxDeltaTorquePerSecond = Math.max(0.0, maxDeltaTorquePerSecond);
    }

    public MotorCommands clamp(MotorCommands raw, SimulationState state) {
        if (raw == null) {
            raw = MotorCommands.ZERO;
        }
        if (state == null) {
            return raw;
        }
        if (latchedHold || !state.getSafetyViolations().isEmpty()) {
            latchedHold = true;
            lastSafeCommands = MotorCommands.ZERO;
            return MotorCommands.ZERO;
        }

        MotorCommands motorLimited = clampToMotorLimits(raw, state);
        MotorCommands rateLimited = applyRateLimit(motorLimited, state);
        lastSafeCommands = rateLimited;
        return rateLimited;
    }

    public void reset() {
        latchedHold = false;
        lastSafeCommands = MotorCommands.ZERO;
    }

    public boolean isLatched() {
        return latchedHold;
    }

    public String mode() {
        return latchedHold ? "HOLD" : "NORMAL";
    }

    private MotorCommands clampToMotorLimits(MotorCommands raw, SimulationState state) {
        Motor hip = state.getExoskeletonModel().getHipMotor();
        Motor knee = state.getExoskeletonModel().getKneeMotor();
        Motor ankle = state.getExoskeletonModel().getAnkleMotor();

        return new MotorCommands(
                clampTorque(raw.hipTorque(), hip.getMaxTorque(), hip.getMaxPower(), state.getHumanModel().getHipJoint()),
                clampTorque(raw.kneeTorque(), knee.getMaxTorque(), knee.getMaxPower(), state.getHumanModel().getKneeJoint()),
                clampTorque(raw.ankleTorque(), ankle.getMaxTorque(), ankle.getMaxPower(), state.getHumanModel().getAnkleJoint())
        );
    }

    private double clampTorque(double torque, double maxTorque, double maxPower, Joint joint) {
        double clamped = Math.max(-maxTorque, Math.min(maxTorque, torque));
        double omega = Math.abs(joint.getAngularVelocity());
        if (omega > 1e-6) {
            double powerLimited = maxPower / omega;
            clamped = Math.max(-powerLimited, Math.min(powerLimited, clamped));
        }
        return clamped;
    }

    private MotorCommands applyRateLimit(MotorCommands raw, SimulationState state) {
        double maxDelta = maxDeltaTorquePerSecond * Math.max(0.0, state.getDt());
        return new MotorCommands(
                limitDelta(lastSafeCommands.hipTorque(), raw.hipTorque(), maxDelta),
                limitDelta(lastSafeCommands.kneeTorque(), raw.kneeTorque(), maxDelta),
                limitDelta(lastSafeCommands.ankleTorque(), raw.ankleTorque(), maxDelta)
        );
    }

    private double limitDelta(double previous, double current, double maxDelta) {
        double delta = current - previous;
        if (delta > maxDelta) {
            return previous + maxDelta;
        }
        if (delta < -maxDelta) {
            return previous - maxDelta;
        }
        return current;
    }
}

