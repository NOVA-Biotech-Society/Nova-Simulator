package simulation.controller;
import simulation.model.SimulationState;
public interface ExoController {
    void reset();
    MotorCommands computeCommands(SimulationState state, double time);
    String getName();

    default String getCurrentKeyframeName() {
        return "N/A";
    }
}