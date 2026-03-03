package simulation.controller;
public record MotorCommands(double hipTorque, double kneeTorque, double ankleTorque) {
    public static final MotorCommands ZERO = new MotorCommands(0, 0, 0);
}