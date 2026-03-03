package simulation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for sensors attached to {@link RigidBodySegment}s.
 * <p>
 * Sensors read data from the simulation state and maintain a history of readings.
 * Concrete subclasses (e.g., {@link ImuSensor}) define what data they produce.
 * </p>
 */
public abstract class Sensor {

    private final String name;
    private final RigidBodySegment attachedSegment;

    // Local offset from segment's proximal end (fraction of segment length, 0..1)
    private double localOffsetFraction;

    /**
     * Creates a new sensor.
     *
     * @param name              human-readable name
     * @param attachedSegment   the segment this sensor is mounted on
     * @param localOffsetFraction position along the segment (0 = proximal, 1 = distal)
     */
    protected Sensor(String name, RigidBodySegment attachedSegment, double localOffsetFraction) {
        this.name = name;
        this.attachedSegment = attachedSegment;
        this.localOffsetFraction = localOffsetFraction;
    }

    /**
     * Updates the sensor reading from the current simulation state.
     *
     * @param dt timestep in seconds
     */
    public abstract void update(double dt);

    /** Resets the sensor state and history. */
    public abstract void reset();

    // ---- Getters ----

    public String getName() { return name; }
    public RigidBodySegment getAttachedSegment() { return attachedSegment; }
    public double getLocalOffsetFraction() { return localOffsetFraction; }
    public void setLocalOffsetFraction(double f) { this.localOffsetFraction = f; }

    /** World X position of the sensor. */
    public double getWorldX() {
        RigidBodySegment seg = attachedSegment;
        return seg.getPosX() + localOffsetFraction * seg.getLength() * Math.sin(seg.getAngle());
    }

    /** World Y position of the sensor. */
    public double getWorldY() {
        RigidBodySegment seg = attachedSegment;
        return seg.getPosY() - localOffsetFraction * seg.getLength() * Math.cos(seg.getAngle());
    }
}
