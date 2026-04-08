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
    private volatile boolean useDelegateCommands = true;

    private double kp = 80.0;
    private double kd = 12.0;

    // Hold gains keep non-selected joints stable when scripted motion is disabled.
    private double holdKp = 50.0;
    private double holdKd = 10.0;

    private Double holdHipAngleRad;
    private Double holdKneeAngleRad;
    private Double holdAnkleAngleRad;

    public HardwareKneeController(ExoController delegate) {
        this.delegate = delegate;
    }

    @Override
    public void reset() {
        delegate.reset();
        clearTargetJointAngle();
        clearHoldTargets();
    }

    @Override
    public MotorCommands computeCommands(SimulationState state, double time) {
        if (useDelegateCommands) {
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

        ensureHoldTargets(state);

        Joint hip = state.getHumanModel().getHipJoint();
        Joint knee = state.getHumanModel().getKneeJoint();
        Joint ankle = state.getHumanModel().getAnkleJoint();

        double hipTorque = holdTorque(holdHipAngleRad, hip);
        double kneeTorque = holdTorque(holdKneeAngleRad, knee);
        double ankleTorque = holdTorque(holdAnkleAngleRad, ankle);

        Double target = targetJointAngleRad.get();
        if (target != null) {
            Joint selected = getJoint(state, controlledJointType);
            double overrideTorque = kp * (target - selected.getAngle()) + kd * (0 - selected.getAngularVelocity());
            switch (controlledJointType) {
                case HIP -> hipTorque = overrideTorque;
                case KNEE -> kneeTorque = overrideTorque;
                case ANKLE -> ankleTorque = overrideTorque;
            }
        }

        return new MotorCommands(hipTorque, kneeTorque, ankleTorque);
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

    public void setUseDelegateCommands(boolean useDelegateCommands) {
        this.useDelegateCommands = useDelegateCommands;
        if (useDelegateCommands) {
            clearHoldTargets();
        }
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

    public void captureHoldTargets(SimulationState state) {
        holdHipAngleRad = state.getHumanModel().getHipJoint().getAngle();
        holdKneeAngleRad = state.getHumanModel().getKneeJoint().getAngle();
        holdAnkleAngleRad = state.getHumanModel().getAnkleJoint().getAngle();
    }

    private void ensureHoldTargets(SimulationState state) {
        if (holdHipAngleRad == null || holdKneeAngleRad == null || holdAnkleAngleRad == null) {
            captureHoldTargets(state);
        }
    }

    private void clearHoldTargets() {
        holdHipAngleRad = null;
        holdKneeAngleRad = null;
        holdAnkleAngleRad = null;
    }

    private double holdTorque(double holdTarget, Joint joint) {
        return holdKp * (holdTarget - joint.getAngle()) + holdKd * (0 - joint.getAngularVelocity());
    }

    private Joint getJoint(SimulationState state, JointType type) {
        return switch (type) {
            case HIP -> state.getHumanModel().getHipJoint();
            case KNEE -> state.getHumanModel().getKneeJoint();
            case ANKLE -> state.getHumanModel().getAnkleJoint();
        };
    }
}
