package simulation.ml;

import simulation.model.ExoskeletonModel;
import simulation.model.HumanModel;
import simulation.model.ImuSensor;
import simulation.model.Joint;
import simulation.model.SimulationState;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Maintains a filtered rolling history of sensor samples and converts it into a TCN tensor.
 */
public class FeatureBuffer {

    private static final double ACCEL_CLAMP = 40.0;
    private static final double GYRO_CLAMP = 20.0;
    private static final double ANGLE_CLAMP = Math.PI;
    private static final double OMEGA_CLAMP = 12.0;
    private static final double ALPHA = 0.35;

    private final int windowSize;
    private final Deque<SensorSnapshot> samples = new ArrayDeque<>();

    private double filteredAx;
    private double filteredAy;
    private double filteredAz;
    private double filteredGx;
    private double filteredGy;
    private double filteredGz;
    private double filteredHipAngle;
    private double filteredKneeAngle;
    private double filteredAnkleAngle;
    private double filteredHipOmega;
    private double filteredKneeOmega;
    private double filteredAnkleOmega;
    private boolean initialized;

    public FeatureBuffer(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive");
        }
        this.windowSize = windowSize;
    }

    public SensorWindow updateAndGet(SimulationState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        SensorSnapshot snapshot = createSnapshot(state);
        samples.addLast(snapshot);
        while (samples.size() > windowSize) {
            samples.removeFirst();
        }
        SensorWindow window = new SensorWindow(windowSize);
        for (SensorSnapshot sample : samples) {
            window.add(sample);
        }
        return window;
    }

    public float[][] buildTensor() {
        float[][] tensor = new float[windowSize][SensorWindow.FEATURE_COUNT];
        int offset = Math.max(0, windowSize - samples.size());
        int row = offset;
        for (SensorSnapshot sample : samples) {
            tensor[row++] = toNormalizedRow(sample);
        }
        return tensor;
    }

    public void reset() {
        samples.clear();
        initialized = false;
        filteredAx = filteredAy = filteredAz = 0.0;
        filteredGx = filteredGy = filteredGz = 0.0;
        filteredHipAngle = filteredKneeAngle = filteredAnkleAngle = 0.0;
        filteredHipOmega = filteredKneeOmega = filteredAnkleOmega = 0.0;
    }

    private SensorSnapshot createSnapshot(SimulationState state) {
        HumanModel human = state.getHumanModel();
        ExoskeletonModel exo = state.getExoskeletonModel();
        ImuSensor imu = exo.getSensors().isEmpty() ? null : exo.getSensors().get(0);

        double ax = imu == null ? 0.0 : sanitize(imu.getAccelX(), ACCEL_CLAMP);
        double ay = imu == null ? 0.0 : sanitize(imu.getAccelY(), ACCEL_CLAMP);
        double az = imu == null ? 0.0 : sanitize(imu.getAccelZ(), ACCEL_CLAMP);
        double gx = imu == null ? 0.0 : sanitize(imu.getGyroX(), GYRO_CLAMP);
        double gy = imu == null ? 0.0 : sanitize(imu.getGyroY(), GYRO_CLAMP);
        double gz = imu == null ? 0.0 : sanitize(imu.getGyroZ(), GYRO_CLAMP);

        Joint hip = human.getHipJoint();
        Joint knee = human.getKneeJoint();
        Joint ankle = human.getAnkleJoint();
        double hipAngle = sanitize(hip.getAngle(), ANGLE_CLAMP);
        double kneeAngle = sanitize(knee.getAngle(), ANGLE_CLAMP);
        double ankleAngle = sanitize(ankle.getAngle(), ANGLE_CLAMP);
        double hipOmega = sanitize(hip.getAngularVelocity(), OMEGA_CLAMP);
        double kneeOmega = sanitize(knee.getAngularVelocity(), OMEGA_CLAMP);
        double ankleOmega = sanitize(ankle.getAngularVelocity(), OMEGA_CLAMP);

        if (!initialized) {
            initialized = true;
            filteredAx = ax;
            filteredAy = ay;
            filteredAz = az;
            filteredGx = gx;
            filteredGy = gy;
            filteredGz = gz;
            filteredHipAngle = hipAngle;
            filteredKneeAngle = kneeAngle;
            filteredAnkleAngle = ankleAngle;
            filteredHipOmega = hipOmega;
            filteredKneeOmega = kneeOmega;
            filteredAnkleOmega = ankleOmega;
        } else {
            filteredAx = smooth(filteredAx, ax);
            filteredAy = smooth(filteredAy, ay);
            filteredAz = smooth(filteredAz, az);
            filteredGx = smooth(filteredGx, gx);
            filteredGy = smooth(filteredGy, gy);
            filteredGz = smooth(filteredGz, gz);
            filteredHipAngle = smooth(filteredHipAngle, hipAngle);
            filteredKneeAngle = smooth(filteredKneeAngle, kneeAngle);
            filteredAnkleAngle = smooth(filteredAnkleAngle, ankleAngle);
            filteredHipOmega = smooth(filteredHipOmega, hipOmega);
            filteredKneeOmega = smooth(filteredKneeOmega, kneeOmega);
            filteredAnkleOmega = smooth(filteredAnkleOmega, ankleOmega);
        }

        return new SensorSnapshot(
                state.getTime(),
                filteredAx,
                filteredAy,
                filteredAz,
                filteredGx,
                filteredGy,
                filteredGz,
                filteredHipAngle,
                filteredKneeAngle,
                filteredAnkleAngle,
                filteredHipOmega,
                filteredKneeOmega,
                filteredAnkleOmega
        );
    }

    private float[] toNormalizedRow(SensorSnapshot sample) {
        return new float[]{
                (float) sample.timestamp(),
                (float) normalize(sample.imuAx(), ACCEL_CLAMP),
                (float) normalize(sample.imuAy(), ACCEL_CLAMP),
                (float) normalize(sample.imuAz(), ACCEL_CLAMP),
                (float) normalize(sample.imuGx(), GYRO_CLAMP),
                (float) normalize(sample.imuGy(), GYRO_CLAMP),
                (float) normalize(sample.imuGz(), GYRO_CLAMP),
                (float) normalize(sample.hipAngle(), ANGLE_CLAMP),
                (float) normalize(sample.kneeAngle(), ANGLE_CLAMP),
                (float) normalize(sample.ankleAngle(), ANGLE_CLAMP),
                (float) normalize(sample.hipOmega(), OMEGA_CLAMP),
                (float) normalize(sample.kneeOmega(), OMEGA_CLAMP),
                (float) normalize(sample.ankleOmega(), OMEGA_CLAMP)
        };
    }

    private double smooth(double previous, double value) {
        return previous + ALPHA * (value - previous);
    }

    private double sanitize(double value, double clamp) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(-clamp, Math.min(clamp, value));
    }

    private double normalize(double value, double clamp) {
        if (clamp <= 1e-9) {
            return value;
        }
        return Math.max(-1.0, Math.min(1.0, value / clamp));
    }

}

