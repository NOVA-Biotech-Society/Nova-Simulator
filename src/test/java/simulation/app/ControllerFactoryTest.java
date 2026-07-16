package simulation.app;

import org.junit.jupiter.api.Test;
import simulation.controller.ExoController;
import simulation.controller.AIProstrationController;
import simulation.controller.ScriptedProstrationController;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerFactoryTest {

    @Test
    void defaultsToScriptedController() {
        SimulationConfig config = SimulationConfig.defaultConfig();

        ExoController controller = ControllerFactory.create(config);

        assertInstanceOf(ScriptedProstrationController.class, controller);
        assertEquals("Scripted Prostration Controller", controller.getName());
    }

    @Test
    void selectsTribuoReadyControllerWhenConfigured() {
        SimulationConfig config = SimulationConfig.defaultConfig();
        config.setControllerMode(SimulationConfig.ControllerMode.TRIBUO);

        ExoController controller = ControllerFactory.create(config);

        assertInstanceOf(AIProstrationController.class, controller);
        assertEquals("AI Prostration Controller", controller.getName());
    }
}

