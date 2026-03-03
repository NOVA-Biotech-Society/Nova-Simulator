package simulation.app;

/**
 * Central configuration object holding all tuneable parameters for the simulation.
 * <p>
 * Use {@link #defaultConfig()} to get a reasonable starting configuration,
 * then adjust individual parameters as needed.
 * </p>
 */
public class SimulationConfig {

    // --- Simulation parameters ---
    private double dt = 0.002;             // timestep in seconds (2 ms = 500 Hz)
    private double gravity = 9.81;          // m/s²

    // --- Human body parameters ---
    private double humanHeight = 1.75;      // meters
    private double humanMass = 75.0;        // kg
    // Segment length ratios (fraction of height) – from Winter's biomechanics data
    private double thighLengthRatio = 0.245;
    private double shankLengthRatio = 0.246;
    private double footHeightRatio  = 0.039;
    // Segment mass ratios (fraction of total mass)
    private double thighMassRatio = 0.10;
    private double shankMassRatio = 0.047;
    private double footMassRatio  = 0.014;

    // --- Exoskeleton parameters ---
    private double exoMassFraction = 0.15;   // exo segment mass as fraction of human segment mass
    private double motorMaxTorque  = 100.0;  // N·m per motor
    private double motorMaxPower   = 200.0;  // W per motor

    // --- Safety thresholds ---
    private double maxSafeHipTorque     = 150.0;   // N·m
    private double maxSafeKneeTorque    = 150.0;
    private double maxSafeAnkleTorque   = 100.0;
    private double maxSafeAngularVel    = 5.0;     // rad/s

    // --- Joint angle limits (degrees, converted to radians in use) ---
    private double hipMinAngle   = -30;
    private double hipMaxAngle   = 130;
    private double kneeMinAngle  = 0;
    private double kneeMaxAngle  = 140;
    private double ankleMinAngle = -50;
    private double ankleMaxAngle = 30;

    // --- Joint mechanical properties ---
    private double jointDampingHip   = 2.0;
    private double jointDampingKnee  = 1.5;
    private double jointDampingAnkle = 1.0;

    /** Returns a default configuration with reasonable values. */
    public static SimulationConfig defaultConfig() {
        return new SimulationConfig();
    }

    // ---- Getters & setters ----

    public double getDt() { return dt; }
    public void setDt(double dt) { this.dt = dt; }

    public double getGravity() { return gravity; }
    public void setGravity(double gravity) { this.gravity = gravity; }

    public double getHumanHeight() { return humanHeight; }
    public void setHumanHeight(double humanHeight) { this.humanHeight = humanHeight; }

    public double getHumanMass() { return humanMass; }
    public void setHumanMass(double humanMass) { this.humanMass = humanMass; }

    public double getThighLengthRatio() { return thighLengthRatio; }
    public double getShankLengthRatio() { return shankLengthRatio; }
    public double getFootHeightRatio() { return footHeightRatio; }

    public double getThighMassRatio() { return thighMassRatio; }
    public double getShankMassRatio() { return shankMassRatio; }
    public double getFootMassRatio() { return footMassRatio; }

    public double getExoMassFraction() { return exoMassFraction; }
    public void setExoMassFraction(double f) { this.exoMassFraction = f; }

    public double getMotorMaxTorque() { return motorMaxTorque; }
    public void setMotorMaxTorque(double t) { this.motorMaxTorque = t; }

    public double getMotorMaxPower() { return motorMaxPower; }
    public void setMotorMaxPower(double p) { this.motorMaxPower = p; }

    public double getMaxSafeAngularVel() { return maxSafeAngularVel; }
    public void setMaxSafeAngularVel(double v) { this.maxSafeAngularVel = v; }

    public double getJointDampingHip() { return jointDampingHip; }
    public double getJointDampingKnee() { return jointDampingKnee; }
    public double getJointDampingAnkle() { return jointDampingAnkle; }

    @Override
    public String toString() {
        return String.format("SimConfig[dt=%.4f human=%.1fm/%.0fkg exoFrac=%.0f%% motorτ=%.0f]",
                dt, humanHeight, humanMass, exoMassFraction * 100, motorMaxTorque);
    }
}

