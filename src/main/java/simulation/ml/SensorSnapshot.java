package simulation.ml;

/**
 * One timestep of sensor and joint state used by the temporal model.
 */
public record SensorSnapshot(
        double timestamp,
        double imuAx,
        double imuAy,
        double imuAz,
        double imuGx,
        double imuGy,
        double imuGz,
        double hipAngle,
        double kneeAngle,
        double ankleAngle,
        double hipOmega,
        double kneeOmega,
        double ankleOmega
) {
}

