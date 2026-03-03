package simulation.physics;

import simulation.model.HumanModel;
import simulation.model.Joint;
import simulation.model.RigidBodySegment;
import simulation.model.SimulationState;

/**
 * Integrates the physics of the simulation using semi-implicit Euler.
 * <p>
 * The integration process for each timestep:
 * <ol>
 *   <li>Clear accumulated forces on all segments.</li>
 *   <li>Apply gravity to all segments.</li>
 *   <li>Apply joint constraint torques (stiffness, damping, limit penalties).</li>
 *   <li>Integrate velocities from forces/torques (semi-implicit: velocity first).</li>
 *   <li>Integrate positions from updated velocities.</li>
 *   <li>Enforce positional constraints (segments connected at joints).</li>
 *   <li>Update joint angles from segment orientations.</li>
 * </ol>
 * </p>
 */
public class PhysicsIntegrator {

    private static final double GRAVITY = -9.81; // m/s² (downward)

    /**
     * Performs one physics integration step.
     *
     * @param state the simulation state to integrate
     * @param dt    timestep in seconds
     */
    public void integrate(SimulationState state, double dt) {
        HumanModel human = state.getHumanModel();

        RigidBodySegment[] segments = human.getAllSegments();
        Joint[] joints = human.getAllJoints();

        // 1. Apply gravity to each segment's center of mass
        //    (forces were already cleared and motor torques applied by SimulationEngine)
        for (RigidBodySegment seg : segments) {
            seg.applyForce(0, GRAVITY * seg.getMass());
        }

        // 3. Apply joint constraint torques
        for (Joint joint : joints) {
            joint.updateFromSegments();
            double constraintTorque = joint.computeConstraintTorque();
            if (joint.getParentSegment() == joint.getChildSegment()) {
                // Hip joint (absolute angle): apply torque directly to the segment
                joint.getChildSegment().applyTorque(constraintTorque);
            } else {
                // Normal joint: torque on child, reaction on parent
                joint.getChildSegment().applyTorque(constraintTorque);
                joint.getParentSegment().applyTorque(-constraintTorque);
            }
        }

        // 4 & 5. Semi-implicit Euler integration for each segment
        for (RigidBodySegment seg : segments) {
            seg.integrateState(dt);
        }

        // 6. Enforce kinematic constraints:
        //    - Hip is pinned (thigh proximal end fixed)
        //    - For a pinned hip, thigh only rotates (no translation)
        //    - Segments chain: thigh→shank→foot

        // Pin thigh: zero out linear velocity and position changes
        RigidBodySegment thigh = human.getThigh();
        thigh.setVelX(0);
        thigh.setVelY(0);

        // Enforce position chain
        human.enforcePositionConstraints();

        // Propagate angular constraint: shank's proximal end velocity must match thigh's distal end velocity
        // This is a simplified constraint - in a full simulation we'd use Lagrangian mechanics
        // For now, we let the joint torques handle velocity coupling

        // 7. Update joint angles from segment orientations
        for (Joint joint : joints) {
            joint.updateFromSegments();
        }

        // 8. Clamp joint angles
        for (Joint joint : joints) {
            joint.clampAngle();
            // Apply clamped angle back to child segment
            applyJointAngleToSegment(joint);
        }

        // Re-enforce positions after angle clamping
        human.enforcePositionConstraints();
    }

    /**
     * Sets the child segment's absolute angle based on the joint angle and parent angle.
     * For normal joints: child absolute angle = parent absolute angle + joint angle.
     * For hip joint (parent == child): the segment's angle IS the joint angle.
     */
    private void applyJointAngleToSegment(Joint joint) {
        if (joint.getParentSegment() == joint.getChildSegment()) {
            // Hip joint: absolute angle
            joint.getChildSegment().setAngle(joint.getAngle());
        } else {
            double parentAngle = joint.getParentSegment().getAngle();
            double childAbsoluteAngle = parentAngle + joint.getAngle();
            joint.getChildSegment().setAngle(childAbsoluteAngle);
        }
    }
}

