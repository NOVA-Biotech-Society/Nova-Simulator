package simulation.physics;

import simulation.model.Joint;
import simulation.model.SimulationState;
import simulation.model.SimulationState.SafetyViolation;
import simulation.model.SimulationState.SafetyViolation.ViolationType;
import simulation.model.Motor;

/**
 * Evaluates safety at each timestep by checking joint angles, torques,
 * and angular velocities against defined thresholds.
 * <p>
 * When a violation is detected, a {@link SafetyViolation} record is added
 * to the simulation state for UI display and logging.
 * </p>
 */
public class SafetyEvaluator {

    /**
     * Evaluates all safety constraints for the current timestep.
     *
     * @param state the current simulation state
     * @return true if any violation was detected
     */
    public boolean evaluate(SimulationState state) {
        boolean anyViolation = false;
        double time = state.getTime();

        // Check each joint
        for (Joint joint : state.getHumanModel().getAllJoints()) {
            // 1. Check joint angle limits
            double angle = joint.getAngle();
            if (angle < joint.getMinAngle()) {
                state.addViolation(new SafetyViolation(
                        time, joint.getName(), ViolationType.ANGLE_EXCEEDED,
                        Math.toDegrees(angle), Math.toDegrees(joint.getMinAngle())));
                anyViolation = true;
            } else if (angle > joint.getMaxAngle()) {
                state.addViolation(new SafetyViolation(
                        time, joint.getName(), ViolationType.ANGLE_EXCEEDED,
                        Math.toDegrees(angle), Math.toDegrees(joint.getMaxAngle())));
                anyViolation = true;
            }

            // 2. Check angular velocity
            double omega = Math.abs(joint.getAngularVelocity());
            if (omega > joint.getMaxSafeAngularVelocity()) {
                state.addViolation(new SafetyViolation(
                        time, joint.getName(), ViolationType.ANGULAR_VELOCITY_EXCEEDED,
                        omega, joint.getMaxSafeAngularVelocity()));
                anyViolation = true;
            }
        }

        // 3. Check motor torques
        for (Motor motor : state.getExoskeletonModel().getAllMotors()) {
            double torque = Math.abs(motor.getOutputTorque());
            Joint joint = motor.getAttachedJoint();
            if (torque > joint.getMaxSafeTorque()) {
                state.addViolation(new SafetyViolation(
                        time, joint.getName(), ViolationType.TORQUE_EXCEEDED,
                        torque, joint.getMaxSafeTorque()));
                anyViolation = true;
            }
        }

        return anyViolation;
    }
}

