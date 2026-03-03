package simulation.model;
import java.util.ArrayList;
import java.util.List;
public class ExoskeletonSegment {
    private final RigidBodySegment rigidBody;
    private final List<Motor> attachedMotors = new ArrayList<>();
    private final List<ImuSensor> attachedSensors = new ArrayList<>();
    private double thickness;
    public ExoskeletonSegment(RigidBodySegment rigidBody, double thickness) {
        this.rigidBody = rigidBody;
        this.thickness = thickness;
    }
    public void addMotor(Motor motor) { attachedMotors.add(motor); }
    public void addSensor(ImuSensor sensor) { attachedSensors.add(sensor); }
    public RigidBodySegment getRigidBody() { return rigidBody; }
    public List<Motor> getAttachedMotors() { return attachedMotors; }
    public List<ImuSensor> getAttachedSensors() { return attachedSensors; }
    public double getThickness() { return thickness; }
    public void setThickness(double thickness) { this.thickness = thickness; }
}