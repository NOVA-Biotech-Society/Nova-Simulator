package simulation.model;

/**
 * Represents a rotational joint connecting two {@link RigidBodySegment}s.
 * <p>
 * Each joint has angle limits (min/max), stiffness, and damping. The angle is defined
 * as the relative angle between the child segment and the parent segment in the sagittal plane.
 * </p>
 * <p>
 * Sign convention: positive angle = flexion direction.
 * </p>
 */
public class Joint {

    private final String name;
    private final JointType type;
    private final RigidBodySegment parentSegment;
    private final RigidBodySegment childSegment;

    // --- Joint angle state ---
    private double angle;           // rad – relative angle between parent and child
    private double angularVelocity; // rad/s

    // --- Limits & mechanical properties ---
    private double minAngle;    // rad – minimum allowed angle (extension limit)
    private double maxAngle;    // rad – maximum allowed angle (flexion limit)
    private double stiffness;   // N·m/rad – passive stiffness (spring-like resistance)
    private double damping;     // N·m·s/rad – viscous damping

    // --- Safety thresholds ---
    private double maxSafeTorque;           // N·m – maximum safe torque
    private double maxSafeAngularVelocity;  // rad/s – maximum safe angular velocity

    /**
     * Creates a new joint.
     *
     * @param name          human-readable name
     * @param type          joint type
     * @param parentSegment parent (proximal) segment
     * @param childSegment  child (distal) segment
     * @param minAngle      minimum angle in radians
     * @param maxAngle      maximum angle in radians
     */
    public Joint(String name, JointType type,
                 RigidBodySegment parentSegment, RigidBodySegment childSegment,
                 double minAngle, double maxAngle) {
        this.name = name;
        this.type = type;
        this.parentSegment = parentSegment;
        this.childSegment = childSegment;
        this.minAngle = minAngle;
        this.maxAngle = maxAngle;
        this.stiffness = 0.0;
        this.damping = 0.5;   // light default damping
        this.maxSafeTorque = 150.0;        // N·m default
        this.maxSafeAngularVelocity = 5.0; // rad/s default
    }

    /**
     * Computes the passive constraint torque from stiffness and damping.
     * Also applies a stiff penalty if the angle exceeds limits.
     *
     * @return torque in N·m to be applied (positive = resists flexion beyond neutral)
     */
    public double computeConstraintTorque() {
        double torque = 0;

        // Passive stiffness (spring about neutral = 0)
        torque -= stiffness * angle;

        // Viscous damping
        torque -= damping * angularVelocity;

        // Hard limit penalty (stiff spring at boundaries)
        double penaltyStiffness = 500.0; // N·m/rad
        if (angle < minAngle) {
            torque += penaltyStiffness * (minAngle - angle);
        } else if (angle > maxAngle) {
            torque += penaltyStiffness * (maxAngle - angle);
        }

        return torque;
    }

    /** Clamps the joint angle to the allowed range. */
    public void clampAngle() {
        if (angle < minAngle) {
            angle = minAngle;
            if (angularVelocity < 0) angularVelocity = 0;
        } else if (angle > maxAngle) {
            angle = maxAngle;
            if (angularVelocity > 0) angularVelocity = 0;
        }
    }

    /**
     * Updates the joint angle from the absolute angles of parent and child segments.
     * <p>
     * For normal joints: angle = child absolute angle - parent absolute angle.<br>
     * For the hip joint (parent == child): angle = segment's absolute angle (relative to vertical).
     * </p>
     */
    public void updateFromSegments() {
        if (parentSegment == childSegment) {
            // Hip joint special case: absolute angle of the segment
            this.angle = childSegment.getAngle();
            this.angularVelocity = childSegment.getAngularVelocity();
        } else {
            this.angle = childSegment.getAngle() - parentSegment.getAngle();
            this.angularVelocity = childSegment.getAngularVelocity() - parentSegment.getAngularVelocity();
        }
    }

    // ---- Getters & setters ----

    public String getName() { return name; }
    public JointType getType() { return type; }
    public RigidBodySegment getParentSegment() { return parentSegment; }
    public RigidBodySegment getChildSegment() { return childSegment; }

    public double getAngle() { return angle; }
    public void setAngle(double angle) { this.angle = angle; }
    public double getAngularVelocity() { return angularVelocity; }
    public void setAngularVelocity(double angularVelocity) { this.angularVelocity = angularVelocity; }

    public double getMinAngle() { return minAngle; }
    public void setMinAngle(double minAngle) { this.minAngle = minAngle; }
    public double getMaxAngle() { return maxAngle; }
    public void setMaxAngle(double maxAngle) { this.maxAngle = maxAngle; }

    public double getStiffness() { return stiffness; }
    public void setStiffness(double stiffness) { this.stiffness = stiffness; }
    public double getDamping() { return damping; }
    public void setDamping(double damping) { this.damping = damping; }

    public double getMaxSafeTorque() { return maxSafeTorque; }
    public void setMaxSafeTorque(double maxSafeTorque) { this.maxSafeTorque = maxSafeTorque; }
    public double getMaxSafeAngularVelocity() { return maxSafeAngularVelocity; }
    public void setMaxSafeAngularVelocity(double maxSafeAngularVelocity) { this.maxSafeAngularVelocity = maxSafeAngularVelocity; }

    @Override
    public String toString() {
        return String.format("Joint[%s type=%s angle=%.1f° range=[%.1f°..%.1f°]]",
                name, type, Math.toDegrees(angle),
                Math.toDegrees(minAngle), Math.toDegrees(maxAngle));
    }
}


