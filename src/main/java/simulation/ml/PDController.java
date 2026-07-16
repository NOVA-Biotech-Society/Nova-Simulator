package simulation.ml;

import simulation.controller.MotorCommands;
import simulation.model.HumanModel;
import simulation.model.Joint;
import simulation.model.SimulationState;

/**
 * Deterministic PD controller that turns joint targets into motor torques.
 */
public class PDController {

    private double hipKp = 80.0;
    private double hipKd = 12.0;
    private double kneeKp = 85.0;
    private double kneeKd = 13.0;
    private double ankleKp = 45.0;
    private double ankleKd = 8.0;

    public MotorCommands computeCommands(SimulationState state, JointTargets targets) {
        HumanModel human = state.getHumanModel();
        return new MotorCommands(
                computeTorque(human.getHipJoint(), targets.hipAngle(), hipKp, hipKd),
                computeTorque(human.getKneeJoint(), targets.kneeAngle(), kneeKp, kneeKd),
                computeTorque(human.getAnkleJoint(), targets.ankleAngle(), ankleKp, ankleKd)
        );
    }

    private double computeTorque(Joint joint, double targetAngle, double kp, double kd) {
        double angleError = targetAngle - joint.getAngle();
        double velocityError = 0.0 - joint.getAngularVelocity();
        return kp * angleError + kd * velocityError;
    }
}

