package simulation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulated Inertial Measurement Unit (IMU) sensor.
 * <p>
 * Derives accelerometer, gyroscope, and magnetometer readings from the rigid body state
 * of the segment it is attached to. All readings are in the sensor's local (body) frame.
 * </p>
 *
 * <ul>
 *   <li><b>Accelerometer</b>: linear acceleration in the local frame (includes gravity).</li>
 *   <li><b>Gyroscope</b>: angular velocity in the local frame.</li>
 *   <li><b>Magnetometer</b>: a fixed global magnetic field vector projected into the local frame.</li>
 * </ul>
 */
public class ImuSensor extends Sensor {

    // --- Current readings (local frame, 3D but z=0 for sagittal) ---
    private double accelX, accelY, accelZ;   // m/s² (local frame)
    private double gyroX, gyroY, gyroZ;      // rad/s (local frame)
    private double magX, magY, magZ;         // arbitrary units (local frame)

    // Global magnetic field (simplified constant)
    private static final double GLOBAL_MAG_X = 0.2;  // pointing "north" in world X
    private static final double GLOBAL_MAG_Y = -0.4;  // pointing slightly down

    // --- History ---
    /** Each entry: [time, accelX, accelY, gyroX, gyroY, magX, magY] */
    private final List<double[]> history = new ArrayList<>();

    // Previous velocities for acceleration computation
    private double prevVelX, prevVelY;

    /**
     * Creates an IMU sensor.
     *
     * @param name              human-readable name
     * @param attachedSegment   the segment this sensor is mounted on
     * @param localOffsetFraction position along the segment (0 = proximal, 1 = distal)
     */
    public ImuSensor(String name, RigidBodySegment attachedSegment, double localOffsetFraction) {
        super(name, attachedSegment, localOffsetFraction);
    }

    @Override
    public void update(double dt) {
        RigidBodySegment seg = getAttachedSegment();
        double angle = seg.getAngle();

        // --- Gyroscope: angular velocity (about Z axis in local frame for sagittal) ---
        gyroX = 0;
        gyroY = 0;
        gyroZ = seg.getAngularVelocity();

        // --- Accelerometer: linear acceleration in local frame ---
        // Compute world-frame acceleration from velocity change (finite difference)
        double worldAccelX = 0;
        double worldAccelY = 0;
        if (dt > 1e-9) {
            worldAccelX = (seg.getVelX() - prevVelX) / dt;
            worldAccelY = (seg.getVelY() - prevVelY) / dt;
        }
        // Add gravity (accelerometer measures specific force = a - g, but senses g when stationary)
        // In world frame, gravity is (0, -9.81). Accelerometer reads: a_measured = a_actual - g
        // But conventionally, a stationary accelerometer reads +g, so: a_measured = a_actual + (0, 9.81)
        worldAccelX += 0;
        worldAccelY += 9.81;

        // Rotate world-frame acceleration into local (body) frame
        double cosA = Math.cos(-angle);
        double sinA = Math.sin(-angle);
        accelX = worldAccelX * cosA - worldAccelY * sinA;
        accelY = worldAccelX * sinA + worldAccelY * cosA;
        accelZ = 0;

        prevVelX = seg.getVelX();
        prevVelY = seg.getVelY();

        // --- Magnetometer: global field projected into local frame ---
        magX = GLOBAL_MAG_X * cosA - GLOBAL_MAG_Y * sinA;
        magY = GLOBAL_MAG_X * sinA + GLOBAL_MAG_Y * cosA;
        magZ = 0;

        // Record history
        history.add(new double[]{
                0, // time placeholder – filled by SimulationEngine
                accelX, accelY, gyroZ, magX, magY
        });
    }

    @Override
    public void reset() {
        accelX = accelY = accelZ = 0;
        gyroX = gyroY = gyroZ = 0;
        magX = magY = magZ = 0;
        prevVelX = prevVelY = 0;
        history.clear();
    }

    /** Stamps the latest history entry with the simulation time. */
    public void stampTime(double time) {
        if (!history.isEmpty()) {
            history.get(history.size() - 1)[0] = time;
        }
    }

    // ---- Getters ----

    public double getAccelX() { return accelX; }
    public double getAccelY() { return accelY; }
    public double getAccelZ() { return accelZ; }
    public double getGyroX() { return gyroX; }
    public double getGyroY() { return gyroY; }
    public double getGyroZ() { return gyroZ; }
    public double getMagX() { return magX; }
    public double getMagY() { return magY; }
    public double getMagZ() { return magZ; }
    public List<double[]> getHistory() { return history; }

    @Override
    public String toString() {
        return String.format("IMU[%s accel=(%.2f,%.2f) gyro=%.2f mag=(%.2f,%.2f)]",
                getName(), accelX, accelY, gyroZ, magX, magY);
    }
}
