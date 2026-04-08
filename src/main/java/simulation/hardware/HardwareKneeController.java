package simulation.hardware;

import simulation.controller.ExoController;
import simulation.controller.MotorCommands;
import simulation.model.Joint;
import simulation.model.JointType;
import simulation.model.SimulationState;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Decorates the default controller and overrides one selected joint from hardware input.
 */
public class HardwareKneeController implements ExoController {

    private final ExoController delegate;
    private final AtomicReference<Double> targetJointAngleRad = new AtomicReference<>(null);
    private volatile JointType controlledJointType = JointType.KNEE;

    private double kp = 80.0;
    private double kd = 12.0;

    public HardwareKneeController(ExoController delegate) {
        this.delegate = delegate;
    }

    @Override
    public void reset() {
        delegate.reset();
        clearTargetJointAngle();
    }

    @Override
    public MotorCommands computeCommands(SimulationState state, double time) {
        MotorCommands base = delegate.computeCommands(state, time);
        Double target = targetJointAngleRad.get();
        if (target == null) {
            return base;
        }

        Joint joint = getJoint(state, controlledJointType);
        double overrideTorque = kp * (target - joint.getAngle()) + kd * (0 - joint.getAngularVelocity());

        return switch (controlledJointType) {
            case HIP -> new MotorCommands(overrideTorque, base.kneeTorque(), base.ankleTorque());
            case KNEE -> new MotorCommands(base.hipTorque(), overrideTorque, base.ankleTorque());
            case ANKLE -> new MotorCommands(base.hipTorque(), base.kneeTorque(), overrideTorque);
        };
    }

    @Override
    public String getName() {
        return delegate.getName() + " + Hardware Joint Override";
    }

    public void setControlledJointType(JointType controlledJointType) {
        this.controlledJointType = controlledJointType != null ? controlledJointType : JointType.KNEE;
    }

    public JointType getControlledJointType() {
        return controlledJointType;
    }

    public void setTargetJointAngleRadians(double angleRadians) {
        targetJointAngleRad.set(angleRadians);
    }

    public void clearTargetJointAngle() {
        targetJointAngleRad.set(null);
    }

    public void setGains(double kp, double kd) {
        this.kp = kp;
        this.kd = kd;
    }

    private Joint getJoint(SimulationState state, JointType type) {
        return switch (type) {
            case HIP -> state.getHumanModel().getHipJoint();
            case KNEE -> state.getHumanModel().getKneeJoint();
            case ANKLE -> state.getHumanModel().getAnkleJoint();
        };
    }
}
