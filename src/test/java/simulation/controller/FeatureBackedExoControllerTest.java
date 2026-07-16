package simulation.controller;

import org.junit.jupiter.api.Test;
import simulation.ml.SimulationCommandPolicy;
import simulation.ml.SimulationFeatureVector;
import simulation.model.ExoskeletonModel;
import simulation.model.HumanModel;
import simulation.model.SimulationState;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureBackedExoControllerTest {

    @Test
    void delegatesFeatureExtractionAndPolicyPrediction() {
        HumanModel human = new HumanModel(1.75, 75.0);
        ExoskeletonModel exo = new ExoskeletonModel(human, 0.15, 100.0, 200.0);
        SimulationState state = new SimulationState(human, exo, 0.001);
        state.reset();
        state.setTime(4.0);

        RecordingPolicy policy = new RecordingPolicy();
        FeatureBackedExoController controller = new FeatureBackedExoController(
                liveState -> {
                    Map<String, Double> features = new LinkedHashMap<>();
                    features.put("time_seconds", liveState.getTime());
                    features.put("hip_angle_rad", liveState.getHumanModel().getHipJoint().getAngle());
                    return SimulationFeatureVector.of(features);
                },
                policy
        );

        MotorCommands commands = controller.computeCommands(state, state.getTime());

        assertEquals(1.0, commands.hipTorque());
        assertEquals(2.0, commands.kneeTorque());
        assertEquals(3.0, commands.ankleTorque());
        assertEquals(4.0, policy.lastFeatures.get("time_seconds"), 1e-12);
        assertFalse(policy.resetCalled);

        controller.reset();
        assertTrue(policy.resetCalled);
    }

    private static final class RecordingPolicy implements SimulationCommandPolicy {
        private SimulationFeatureVector lastFeatures;
        private boolean resetCalled;

        @Override
        public MotorCommands computeCommands(SimulationFeatureVector features) {
            lastFeatures = features;
            return new MotorCommands(1.0, 2.0, 3.0);
        }

        @Override
        public String getName() {
            return "Recording Policy";
        }

        @Override
        public void reset() {
            resetCalled = true;
        }
    }
}
