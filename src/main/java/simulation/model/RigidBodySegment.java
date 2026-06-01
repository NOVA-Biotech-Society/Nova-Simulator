package simulation.model;

/**
 * Represents a rigid body segment in the simulation (e.g., thigh, shank, foot).
 * <p>
 * Each segment has physical properties (mass, length, inertia) and kinematic state
 * (position, orientation, velocity, angular velocity). The simulation operates primarily
 * in the sagittal plane (2D physics rendered in 3D).
 * </p>
 */
public class RigidBodySegment {

    private final String name;

    // --- Physical properties ---
    private double mass;       // kg
    private double length;     // m (along the segment's local Y axis)
    private double width;      // m (cross-section diameter/width for visualization)
    private double inertia;    // kg·m² (scalar, about the segment's center of mass, sagittal plane)

    // --- Kinematic state (sagittal plane) ---
    // Position of the segment's proximal (top) end in world coordinates
    private double posX;       // m
    private double posY;       // m
    private double posZ;       // m (depth, usually 0 for sagittal plane)

    // Orientation: angle in radians measured from the vertical (positive = forward/flexion)
    private double angle;      // rad

    // Velocities
    private double velX;       // m/s
    private double velY;       // m/s
    private double angularVelocity; // rad/s

    // Accumulated forces and torques for the current timestep
    private double forceX;     // N
    private double forceY;     // N
    private double torque;     // N·m

    /**
     * Creates a new rigid body segment.
     *
     * @param name   human-readable name (e.g., "Thigh", "Shank")
     * @param mass   mass in kg
     * @param length length in meters
     * @param width  cross-section width in meters (for rendering)
     */
    public RigidBodySegment(String name, double mass, double length, double width) {
        this.name = name;
        this.mass = mass;
        this.length = length;
        this.width = width;
        // Approximate inertia as a uniform rod rotating about its center: (1/12) * m * L²
        this.inertia = (1.0 / 12.0) * mass * length * length;
    }

    // ---- Force / torque accumulation (called before integration each step) ----

    /** Resets accumulated forces and torques to zero. Call at the start of each timestep. */
    public void clearForces() {
        forceX = 0;
        forceY = 0;
        torque = 0;
    }

    /** Adds a force (world frame) applied at the center of mass. */
    public void applyForce(double fx, double fy) {
        forceX += fx;
        forceY += fy;
    }

    /** Adds a torque about the segment's center of mass. */
    public void applyTorque(double t) {
        torque += t;
    }

    // ---- Integration (semi-implicit Euler) ----

    /**
     * Integrates velocities and positions using semi-implicit Euler.
     * <p>
     * 1. Update velocity from accumulated force/torque.<br>
     * 2. Update position/angle from the *new* velocity.
     * </p>
     *
     * @param dt timestep in seconds
     */
    public void integrateState(double dt) {
        // Linear
        double ax = forceX / mass;
        double ay = forceY / mass;
        velX += ax * dt;
        velY += ay * dt;
        posX += velX * dt;
        posY += velY * dt;

        // Angular
        double alpha = torque / inertia;
        angularVelocity += alpha * dt;
        angle += angularVelocity * dt;
    }

    // ---- Convenience: compute center-of-mass position ----

    /** X position of center of mass (assumes segment hangs from proximal end along angle). */
    public double getCenterX() {
        return posX + 0.5 * length * Math.sin(angle);
    }

    /** Y position of center of mass. */
    public double getCenterY() {
        return posY - 0.5 * length * Math.cos(angle);
    }

    /** X position of the distal (bottom) end. */
    public double getDistalX() {
        return posX + length * Math.sin(angle);
    }

    /** Y position of the distal (bottom) end. */
    public double getDistalY() {
        return posY - length * Math.cos(angle);
    }

    // ---- Getters & setters ----

    public String getName() { return name; }
    public double getMass() { return mass; }
    public void setMass(double mass) {
        this.mass = mass;
        this.inertia = (1.0 / 12.0) * mass * length * length;
    }
    public double getLength() { return length; }
    public void setLength(double length) {
        this.length = length;
        this.inertia = (1.0 / 12.0) * mass * length * length;
    }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getInertia() { return inertia; }

    public double getPosX() { return posX; }
    public void setPosX(double posX) { this.posX = posX; }
    public double getPosY() { return posY; }
    public void setPosY(double posY) { this.posY = posY; }
    public double getPosZ() { return posZ; }
    public void setPosZ(double posZ) { this.posZ = posZ; }

    public double getAngle() { return angle; }
    public void setAngle(double angle) { this.angle = angle; }

    public double getVelX() { return velX; }
    public void setVelX(double velX) { this.velX = velX; }
    public double getVelY() { return velY; }
    public void setVelY(double velY) { this.velY = velY; }
    public double getAngularVelocity() { return angularVelocity; }
    public void setAngularVelocity(double angularVelocity) { this.angularVelocity = angularVelocity; }

    public double getForceX() { return forceX; }
    public double getForceY() { return forceY; }
    public double getTorque() { return torque; }

    @Override
    public String toString() {
        return String.format("Segment[%s pos=(%.3f,%.3f) angle=%.2f° ω=%.2f]",
                name, posX, posY, Math.toDegrees(angle), angularVelocity);
    }
}
