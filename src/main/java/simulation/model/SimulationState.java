package simulation.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregates the entire simulation state: human model, exoskeleton model,
 * current time, safety violations, and history.
 */
public class SimulationState {

    private final HumanModel humanModel;
    private final ExoskeletonModel exoskeletonModel;

    private double time;       // current simulation time in seconds
    private double dt;         // timestep in seconds

    private final List<SafetyViolation> safetyViolations = new ArrayList<>();

    // Hardware mode options (default keeps legacy behavior).
    private volatile boolean allowHardwareJointLimitExceedance;
    private volatile JointType hardwareControlledJoint = JointType.KNEE;

    /**
     * Creates a simulation state.
     *
     * @param humanModel       the human leg model
     * @param exoskeletonModel the exoskeleton model
     * @param dt               physics timestep in seconds
     */
    public SimulationState(HumanModel humanModel, ExoskeletonModel exoskeletonModel, double dt) {
        this.humanModel = humanModel;
        this.exoskeletonModel = exoskeletonModel;
        this.dt = dt;
        this.time = 0;
    }

    /** Resets the simulation to initial conditions. */
    public void reset() {
        time = 0;
        humanModel.resetToStandingPose();
        exoskeletonModel.reset();
        safetyViolations.clear();
        allowHardwareJointLimitExceedance = false;
        hardwareControlledJoint = JointType.KNEE;
    }

    public void addViolation(SafetyViolation v) { safetyViolations.add(v); }
    public void clearCurrentViolations() {
        clearSafetyViolations();
    }

    public void clearSafetyViolations() {
        safetyViolations.clear();
    }

    // ---- Getters & setters ----

    public HumanModel getHumanModel() { return humanModel; }
    public ExoskeletonModel getExoskeletonModel() { return exoskeletonModel; }
    public double getTime() { return time; }
    public void setTime(double time) { this.time = time; }
    public void advanceTime() { this.time += dt; }
    public double getDt() { return dt; }
    public void setDt(double dt) { this.dt = dt; }
    public List<SafetyViolation> getSafetyViolations() { return Collections.unmodifiableList(safetyViolations); }

    public boolean isAllowHardwareJointLimitExceedance() {
        return allowHardwareJointLimitExceedance;
    }

    public void setAllowHardwareJointLimitExceedance(boolean allowHardwareJointLimitExceedance) {
        this.allowHardwareJointLimitExceedance = allowHardwareJointLimitExceedance;
    }

    public JointType getHardwareControlledJoint() {
        return hardwareControlledJoint;
    }

    public void setHardwareControlledJoint(JointType hardwareControlledJoint) {
        this.hardwareControlledJoint = hardwareControlledJoint;
    }

    /**
     * Record of a safety violation detected during simulation.
     */
    public record SafetyViolation(
            double time,
            String jointName,
            ViolationType type,
            double value,
            double limit
    ) {
        public enum ViolationType {
            ANGLE_EXCEEDED,
            TORQUE_EXCEEDED,
            ANGULAR_VELOCITY_EXCEEDED
        }

        @Override
        public String toString() {
            return String.format("[t=%.3fs] %s: %s (value=%.2f, limit=%.2f)",
                    time, jointName, type, value, limit);
        }
    }
}
