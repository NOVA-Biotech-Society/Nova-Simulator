package simulation.hardware;

import simulation.controller.ExoController;
import simulation.controller.MotorCommands;
import simulation.model.Joint;
import simulation.model.SimulationState;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Decorates the default controller and overrides only knee torque when a hardware target is available.
 */
public class HardwareKneeController implements ExoController {

    private final ExoController delegate;
    private final AtomicReference<Double> targetKneeAngleRad = new AtomicReference<>(null);

    private double kpKnee = 80.0;
    private double kdKnee = 12.0;

    public HardwareKneeController(ExoController delegate) {
        this.delegate = delegate;
    }

    @Override
    public void reset() {
        delegate.reset();
        clearTargetKneeAngle();
    }

    @Override
    public MotorCommands computeCommands(SimulationState state, double time) {
        MotorCommands base = delegate.computeCommands(state, time);
        Double target = targetKneeAngleRad.get();
        if (target == null) {
            return base;
        }

        Joint knee = state.getHumanModel().getKneeJoint();
        double kneeTorque = kpKnee * (target - knee.getAngle()) + kdKnee * (0 - knee.getAngularVelocity());

        return new MotorCommands(base.hipTorque(), kneeTorque, base.ankleTorque());
    }

    @Override
    public String getName() {
        return delegate.getName() + " + Hardware Knee Override";
    }

    public void setTargetKneeAngleRadians(double angleRadians) {
        targetKneeAngleRad.set(angleRadians);
    }

    public void clearTargetKneeAngle() {
        targetKneeAngleRad.set(null);
    }

    public void setKneeGains(double kp, double kd) {
        this.kpKnee = kp;
        this.kdKnee = kd;
    }
}

