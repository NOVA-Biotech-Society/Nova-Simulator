package simulation.app;

import simulation.controller.ExoController;
import simulation.controller.AIProstrationController;
import simulation.controller.ScriptedProstrationController;

/**
 * Centralizes controller wiring so the simulator can switch between scripted and
 * Tribuo-ready control without touching the engine or the view layer.
 */
public final class ControllerFactory {

    private ControllerFactory() {
        // Utility class.
    }

    public static ExoController create(SimulationConfig config) {
        if (config.getControllerMode() == SimulationConfig.ControllerMode.TRIBUO) {
            return new AIProstrationController(
                    config.getTribuoModelPath(),
                    config.getAiWindowSize(),
                    config.getAiConfidenceThreshold(),
                    config.getAiTelemetryPath()
            );
        }
        return new ScriptedProstrationController();
    }
}

