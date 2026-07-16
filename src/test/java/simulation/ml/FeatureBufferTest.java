package simulation.ml;

import org.junit.jupiter.api.Test;
import simulation.model.ExoskeletonModel;
import simulation.model.HumanModel;
import simulation.model.SimulationState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureBufferTest {

    @Test
    void buildsAStableTemporalTensor() {
        HumanModel human = new HumanModel(1.80, 82.0);
        ExoskeletonModel exo = new ExoskeletonModel(human, 0.15, 120.0, 250.0);
        SimulationState state = new SimulationState(human, exo, 0.01);
        state.reset();

        FeatureBuffer buffer = new FeatureBuffer(4);
        for (int i = 0; i < 3; i++) {
            state.setTime(i * 0.01);
            human.getHipJoint().setAngle(Math.toRadians(5 + i));
            human.getKneeJoint().setAngle(Math.toRadians(10 + i));
            buffer.updateAndGet(state);
        }

        float[][] tensor = buffer.buildTensor();

        assertEquals(4, tensor.length);
        assertEquals(13, tensor[0].length);
        assertEquals(0.0f, tensor[0][1], 1e-6f, "left padding should remain zero");
        assertTrue(tensor[3][7] > 0.0f, "latest hip angle should be encoded into the tensor");
    }
}

