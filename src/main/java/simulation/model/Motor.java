package simulation.model;

/**
 * Represents a motor attached to a {@link Joint}.
 * <p>
 * Motors apply torque at joints to drive movement. They have physical limits
 * (max torque, max power) and accept control commands (desired torque).
 * </p>
 */
public class Motor {

    private final String name;
    private final Joint attachedJoint;

    // --- Motor specifications ---
    private double maxTorque;    // N·m – maximum torque the motor can produce
    private double maxPower;     // W – maximum power output

    // --- Control state ---
    private double commandTorque;   // N·m – desired torque from the controller
    private double outputTorque;    // N·m – actual torque after clamping to limits

    /**
     * Creates a new motor.
     *
     * @param name          human-readable name
     * @param attachedJoint the joint this motor drives
     * @param maxTorque     maximum torque in N·m
     * @param maxPower      maximum power in W
     */
    public Motor(String name, Joint attachedJoint, double maxTorque, double maxPower) {
        this.name = name;
        this.attachedJoint = attachedJoint;
        this.maxTorque = maxTorque;
        this.maxPower = maxPower;
    }

    /**
     * Sets the desired torque command and computes the actual output torque,
     * clamped to the motor's physical limits.
     *
     * @param desiredTorque the torque requested by the controller (N·m)
     */
    public void applyCommand(double desiredTorque) {
        this.commandTorque = desiredTorque;

        // Clamp to max torque
        double clamped = Math.max(-maxTorque, Math.min(maxTorque, desiredTorque));

        // Clamp to max power: P = τ * ω → |τ| ≤ P / |ω|
        double omega = Math.abs(attachedJoint.getAngularVelocity());
        if (omega > 1e-6) {
            double maxTorqueFromPower = maxPower / omega;
            clamped = Math.max(-maxTorqueFromPower, Math.min(maxTorqueFromPower, clamped));
        }

        this.outputTorque = clamped;
    }

    /** Applies the computed output torque to the attached joint's segments. */
    public void applyToJoint() {
        if (attachedJoint.getParentSegment() == attachedJoint.getChildSegment()) {
            // Hip joint (absolute angle): apply torque directly
            attachedJoint.getChildSegment().applyTorque(outputTorque);
        } else {
            // Normal joint: torque on child, reaction on parent
            attachedJoint.getChildSegment().applyTorque(outputTorque);
            attachedJoint.getParentSegment().applyTorque(-outputTorque);
        }
    }

    // ---- Getters & setters ----

    public String getName() { return name; }
    public Joint getAttachedJoint() { return attachedJoint; }
    public double getMaxTorque() { return maxTorque; }
    public void setMaxTorque(double maxTorque) { this.maxTorque = maxTorque; }
    public double getMaxPower() { return maxPower; }
    public void setMaxPower(double maxPower) { this.maxPower = maxPower; }
    public double getCommandTorque() { return commandTorque; }
    public double getOutputTorque() { return outputTorque; }

    @Override
    public String toString() {
        return String.format("Motor[%s cmd=%.2f out=%.2f maxτ=%.1f]",
                name, commandTorque, outputTorque, maxTorque);
    }
}


