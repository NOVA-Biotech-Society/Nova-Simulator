package simulation.controller;

import org.junit.jupiter.api.Test;
import simulation.ml.AIInferenceEngine;
import simulation.ml.AIOutput;
import simulation.ml.Keyframe;
import simulation.ml.SensorWindow;
import simulation.model.ExoskeletonModel;
import simulation.model.HumanModel;
import simulation.model.SimulationState;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIProstrationControllerTest {

    @Test
    void fallsBackOnLowConfidenceAndExposesDiagnostics() throws Exception {
        HumanModel human = new HumanModel(1.75, 75.0);
        ExoskeletonModel exo = new ExoskeletonModel(human, 0.15, 100.0, 200.0);
        SimulationState state = new SimulationState(human, exo, 0.01);
        state.reset();

        Path telemetry = Files.createTempFile("nova-ai-telemetry", ".csv");
        AIInferenceEngine unavailableEngine = new AIInferenceEngine() {
            @Override
            public AIOutput predict(SensorWindow window) {
                return AIOutput.safeHold();
            }

            @Override
            public String getName() {
                return "Unavailable Test Engine";
            }

            @Override
            public boolean isReady() {
                return false;
            }
        };

        AIProstrationController controller = new AIProstrationController(
                null,
                new simulation.ml.FeatureBuffer(8),
                unavailableEngine,
                new simulation.ml.ReferenceTrajectory(),
                new simulation.ml.PDController(),
                new simulation.ml.SafetyLimiter(250.0),
                0.9,
                telemetry
        );

        MotorCommands commands = controller.computeCommands(state, state.getTime());
        controller.recordStep(state);

        assertEquals(MotorCommands.ZERO, commands);
        assertEquals("CONFIDENCE_HOLD", controller.getDiagnostics().safetyMode());
        assertFalse(controller.getDiagnostics().modelReady());
        assertTrue(Files.size(telemetry) > 0, "telemetry CSV should be written");
    }

    @Test
    void producesAssistanceForConfidentPredictions() {
        HumanModel human = new HumanModel(1.75, 75.0);
        ExoskeletonModel exo = new ExoskeletonModel(human, 0.15, 100.0, 200.0);
        SimulationState state = new SimulationState(human, exo, 0.01);
        state.reset();

        AIInferenceEngine engine = new AIInferenceEngine() {
            @Override
            public AIOutput predict(SensorWindow window) {
                return new AIOutput(
                        Keyframe.STANDING,
                        Keyframe.DESCENDING,
                        1.0,
                        0.99,
                        Math.toRadians(25.0),
                        Math.toRadians(30.0),
                        Math.toRadians(-5.0)
                );
            }

            @Override
            public String getName() {
                return "Confident Test Engine";
            }
        };

        AIProstrationController controller = new AIProstrationController(
                Path.of("models", "missing.onnx"),
                new simulation.ml.FeatureBuffer(8),
                engine,
                new simulation.ml.ReferenceTrajectory(),
                new simulation.ml.PDController(),
                new simulation.ml.SafetyLimiter(250.0),
                0.75,
                null
        );

        MotorCommands commands = controller.computeCommands(state, state.getTime());

        assertTrue(Math.abs(commands.hipTorque()) > 0.0);
        assertTrue(Math.abs(commands.kneeTorque()) > 0.0);
        assertEquals("NORMAL", controller.getDiagnostics().safetyMode());
    }
}

