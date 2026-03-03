package simulation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the exoskeleton model that attaches to a {@link HumanModel}.
 * <p>
 * The exoskeleton has its own segments (mirroring the human leg), motors at each joint,
 * and IMU sensors at configurable locations. The exoskeleton segments add mass to the
 * overall system and are co-located with the human segments.
 * </p>
 */
public class ExoskeletonModel {

    private final ExoskeletonSegment thighExo;
    private final ExoskeletonSegment shankExo;
    private final ExoskeletonSegment footExo;

    private final Motor hipMotor;
    private final Motor kneeMotor;
    private final Motor ankleMotor;

    private final List<ImuSensor> sensors = new ArrayList<>();

    /**
     * Creates an exoskeleton model that mirrors a human model.
     *
     * @param humanModel  the human model this exoskeleton attaches to
     * @param exoMassFraction fraction of each human segment's mass for the exo frame (e.g., 0.15 = 15%)
     * @param motorMaxTorque  max torque for each motor (N·m)
     * @param motorMaxPower   max power for each motor (W)
     */
    public ExoskeletonModel(HumanModel humanModel, double exoMassFraction,
                            double motorMaxTorque, double motorMaxPower) {
        // Create exo segments (lighter versions mirroring human segments)
        RigidBodySegment humanThigh = humanModel.getThigh();
        RigidBodySegment humanShank = humanModel.getShank();
        RigidBodySegment humanFoot  = humanModel.getFoot();

        RigidBodySegment thighRB = new RigidBodySegment("ExoThigh",
                humanThigh.getMass() * exoMassFraction,
                humanThigh.getLength(), humanThigh.getWidth() + 0.02);

        RigidBodySegment shankRB = new RigidBodySegment("ExoShank",
                humanShank.getMass() * exoMassFraction,
                humanShank.getLength(), humanShank.getWidth() + 0.02);

        RigidBodySegment footRB = new RigidBodySegment("ExoFoot",
                humanFoot.getMass() * exoMassFraction,
                humanFoot.getLength(), humanFoot.getWidth() + 0.02);

        thighExo = new ExoskeletonSegment(thighRB, 0.005);
        shankExo = new ExoskeletonSegment(shankRB, 0.004);
        footExo  = new ExoskeletonSegment(footRB, 0.003);

        // Create motors at each joint
        hipMotor   = new Motor("HipMotor",   humanModel.getHipJoint(),   motorMaxTorque, motorMaxPower);
        kneeMotor  = new Motor("KneeMotor",  humanModel.getKneeJoint(),  motorMaxTorque, motorMaxPower);
        ankleMotor = new Motor("AnkleMotor", humanModel.getAnkleJoint(), motorMaxTorque, motorMaxPower);

        thighExo.addMotor(hipMotor);
        shankExo.addMotor(kneeMotor);
        footExo.addMotor(ankleMotor);

        // Add default IMU sensors (one per segment, at midpoint)
        ImuSensor thighIMU = new ImuSensor("ThighIMU", humanThigh, 0.5);
        ImuSensor shankIMU = new ImuSensor("ShankIMU", humanShank, 0.5);
        ImuSensor footIMU  = new ImuSensor("FootIMU",  humanFoot,  0.5);

        thighExo.addSensor(thighIMU);
        shankExo.addSensor(shankIMU);
        footExo.addSensor(footIMU);

        sensors.add(thighIMU);
        sensors.add(shankIMU);
        sensors.add(footIMU);
    }

    /** Resets all sensors. */
    public void reset() {
        for (ImuSensor s : sensors) {
            s.reset();
        }
    }

    /** Updates all sensors. */
    public void updateSensors(double dt, double time) {
        for (ImuSensor s : sensors) {
            s.update(dt);
            s.stampTime(time);
        }
    }

    // ---- Getters ----

    public ExoskeletonSegment getThighExo() { return thighExo; }
    public ExoskeletonSegment getShankExo() { return shankExo; }
    public ExoskeletonSegment getFootExo()  { return footExo; }

    public Motor getHipMotor()   { return hipMotor; }
    public Motor getKneeMotor()  { return kneeMotor; }
    public Motor getAnkleMotor() { return ankleMotor; }

    public Motor[] getAllMotors() { return new Motor[]{ hipMotor, kneeMotor, ankleMotor }; }
    public List<ImuSensor> getSensors() { return sensors; }
}

