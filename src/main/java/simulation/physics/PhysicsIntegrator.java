package simulation.physics;

import simulation.model.HumanModel;
import simulation.model.Joint;
import simulation.model.RigidBodySegment;
import simulation.model.SimulationState;

/**
 * Integrates the physics of a pinned leg chain using angular dynamics only.
 * <p>
 * Each segment is treated as a physical pendulum rotating about its proximal
 * pivot point. The only degree of freedom per segment is its angle.
 * Gravity creates a torque about the pivot; linear positions are derived
 * from the chain geometry (no linear force integration).
 * </p>
 * <p>
 * Integration order each timestep:
 * <ol>
 *   <li>Compute gravity torques on each segment about its pivot.</li>
 *   <li>Add joint constraint torques (damping, limit penalties).</li>
 *   <li>Integrate angular velocity and angle (semi-implicit Euler).</li>
 *   <li>Clamp joint angles to physiological limits.</li>
 *   <li>Derive segment positions from the chain geometry.</li>
 * </ol>
 * </p>
 */
public class PhysicsIntegrator {

    private static final double GRAVITY = 9.81; // m/s² magnitude

    /**
     * Performs one physics integration step.
     *
     * @param state the simulation state to integrate
     * @param dt    timestep in seconds
     */
    public void integrate(SimulationState state, double dt) {
        HumanModel human = state.getHumanModel();

        RigidBodySegment thigh = human.getThigh();
        RigidBodySegment shank = human.getShank();
        RigidBodySegment foot  = human.getFoot();

        Joint hipJoint   = human.getHipJoint();
        Joint kneeJoint  = human.getKneeJoint();
        Joint ankleJoint = human.getAnkleJoint();

        // ---------------------------------------------------------------
        // 1. Compute net torque on each segment about its own pivot
        //    (proximal end = joint above).
        //    Motor torques are already accumulated on segments via
        //    seg.applyTorque() before this method is called.
        // ---------------------------------------------------------------

        // -- Thigh: pivots about hip anchor --
        // Gravity torque = m * g * (L/2) * sin(angle)
        //   angle=0 → vertical → no gravity torque
        //   angle>0 → forward lean → positive torque (pulls further forward)
        double thighGravTorque = thigh.getMass() * GRAVITY
                * (thigh.getLength() / 2.0) * Math.sin(thigh.getAngle());
        // Also account for the weight of shank+foot hanging at the thigh's distal end
        double childMass = shank.getMass() + foot.getMass();
        thighGravTorque += childMass * GRAVITY
                * thigh.getLength() * Math.sin(thigh.getAngle());

        // -- Shank: pivots about knee (thigh distal end) --
        double shankGravTorque = shank.getMass() * GRAVITY
                * (shank.getLength() / 2.0) * Math.sin(shank.getAngle());
        double footMass = foot.getMass();
        shankGravTorque += footMass * GRAVITY
                * shank.getLength() * Math.sin(shank.getAngle());

        // -- Foot: pivots about ankle (shank distal end) --
        double footGravTorque = foot.getMass() * GRAVITY
                * (foot.getLength() / 2.0) * Math.sin(foot.getAngle());

        // ---------------------------------------------------------------
        // 2. Add joint constraint torques (damping + limit penalties)
        // ---------------------------------------------------------------
        hipJoint.updateFromSegments();
        kneeJoint.updateFromSegments();
        ankleJoint.updateFromSegments();

        double hipConstraint   = hipJoint.computeConstraintTorque();
        double kneeConstraint  = kneeJoint.computeConstraintTorque();
        double ankleConstraint = ankleJoint.computeConstraintTorque();

        // ---------------------------------------------------------------
        // 3. Compute effective inertia for each segment about its pivot.
        //    I_pivot = I_cm + m * (L/2)^2  (parallel axis theorem)
        //    Plus contribution from children hanging at the distal end.
        // ---------------------------------------------------------------
        double thighIpivot = thigh.getInertia()
                + thigh.getMass() * Math.pow(thigh.getLength() / 2.0, 2)
                + childMass * Math.pow(thigh.getLength(), 2);

        double shankIpivot = shank.getInertia()
                + shank.getMass() * Math.pow(shank.getLength() / 2.0, 2)
                + footMass * Math.pow(shank.getLength(), 2);

        double footIpivot = foot.getInertia()
                + foot.getMass() * Math.pow(foot.getLength() / 2.0, 2);

        // ---------------------------------------------------------------
        // 4. Compute total torque on each segment and integrate
        // ---------------------------------------------------------------

        // Thigh: gravity + hip constraint + motor torque (from seg.getTorque())
        //   Hip constraint acts on thigh directly (parent==child special case).
        //   Knee constraint: reaction torque on thigh is -kneeConstraint.
        double thighNetTorque = thighGravTorque
                + hipConstraint
                - kneeConstraint   // reaction from knee joint
                + thigh.getTorque(); // motor + external torques

        double thighAlpha = thighNetTorque / thighIpivot;
        thigh.setAngularVelocity(thigh.getAngularVelocity() + thighAlpha * dt);
        thigh.setAngle(thigh.getAngle() + thigh.getAngularVelocity() * dt);

        // Shank: gravity + knee constraint + ankle reaction + motor torque
        double shankNetTorque = shankGravTorque
                + kneeConstraint
                - ankleConstraint  // reaction from ankle joint
                + shank.getTorque();

        double shankAlpha = shankNetTorque / shankIpivot;
        shank.setAngularVelocity(shank.getAngularVelocity() + shankAlpha * dt);
        shank.setAngle(shank.getAngle() + shank.getAngularVelocity() * dt);

        // Foot: gravity + ankle constraint + motor torque
        double footNetTorque = footGravTorque
                + ankleConstraint
                + foot.getTorque();

        double footAlpha = footNetTorque / footIpivot;
        foot.setAngularVelocity(foot.getAngularVelocity() + footAlpha * dt);
        foot.setAngle(foot.getAngle() + foot.getAngularVelocity() * dt);

        // ---------------------------------------------------------------
        // 5. Update joint angles from segment absolute angles, then clamp
        // ---------------------------------------------------------------
        hipJoint.updateFromSegments();
        kneeJoint.updateFromSegments();
        ankleJoint.updateFromSegments();

        // Clamp hip
        if (hipJoint.getAngle() < hipJoint.getMinAngle()) {
            thigh.setAngle(hipJoint.getMinAngle());
            if (thigh.getAngularVelocity() < 0) thigh.setAngularVelocity(0);
        } else if (hipJoint.getAngle() > hipJoint.getMaxAngle()) {
            thigh.setAngle(hipJoint.getMaxAngle());
            if (thigh.getAngularVelocity() > 0) thigh.setAngularVelocity(0);
        }

        // Clamp knee
        kneeJoint.updateFromSegments();
        if (kneeJoint.getAngle() < kneeJoint.getMinAngle()) {
            shank.setAngle(thigh.getAngle() + kneeJoint.getMinAngle());
            if (shank.getAngularVelocity() - thigh.getAngularVelocity() < 0) {
                shank.setAngularVelocity(thigh.getAngularVelocity());
            }
        } else if (kneeJoint.getAngle() > kneeJoint.getMaxAngle()) {
            shank.setAngle(thigh.getAngle() + kneeJoint.getMaxAngle());
            if (shank.getAngularVelocity() - thigh.getAngularVelocity() > 0) {
                shank.setAngularVelocity(thigh.getAngularVelocity());
            }
        }

        // Clamp ankle
        ankleJoint.updateFromSegments();
        if (ankleJoint.getAngle() < ankleJoint.getMinAngle()) {
            foot.setAngle(shank.getAngle() + ankleJoint.getMinAngle());
            if (foot.getAngularVelocity() - shank.getAngularVelocity() < 0) {
                foot.setAngularVelocity(shank.getAngularVelocity());
            }
        } else if (ankleJoint.getAngle() > ankleJoint.getMaxAngle()) {
            foot.setAngle(shank.getAngle() + ankleJoint.getMaxAngle());
            if (foot.getAngularVelocity() - shank.getAngularVelocity() > 0) {
                foot.setAngularVelocity(shank.getAngularVelocity());
            }
        }

        // Final joint angle update
        hipJoint.updateFromSegments();
        kneeJoint.updateFromSegments();
        ankleJoint.updateFromSegments();

        // ---------------------------------------------------------------
        // 6. Derive positions from chain geometry (angles only)
        // ---------------------------------------------------------------
        human.enforcePositionConstraints();
    }
}

