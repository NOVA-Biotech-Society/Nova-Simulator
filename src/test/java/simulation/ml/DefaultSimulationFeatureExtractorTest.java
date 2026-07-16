package simulation.ml;

import org.junit.jupiter.api.Test;
import simulation.model.ExoskeletonModel;
import simulation.model.HumanModel;
import simulation.model.JointType;
import simulation.model.SimulationState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSimulationFeatureExtractorTest {

    @Test
    void extractsStableAndMeaningfulFeatureSnapshot() {
        HumanModel human = new HumanModel(1.80, 82.0);
        ExoskeletonModel exo = new ExoskeletonModel(human, 0.15, 120.0, 250.0);
        SimulationState state = new SimulationState(human, exo, 0.002);
        state.reset();

        state.setTime(2.5);
        state.setAllowHardwareJointLimitExceedance(true);
        state.setHardwareControlledJoint(JointType.ANKLE);

        human.getHipJoint().setAngle(Math.toRadians(18));
        human.getHipJoint().setAngularVelocity(0.35);
        human.getKneeJoint().setAngle(Math.toRadians(33));
        human.getKneeJoint().setAngularVelocity(0.22);
        human.getAnkleJoint().setAngle(Math.toRadians(-8));
        human.getAnkleJoint().setAngularVelocity(-0.11);

        exo.getKneeMotor().getAttachedJoint().setAngularVelocity(0.5);
        exo.getKneeMotor().applyCommand(12.5);

        SimulationFeatureVector features = new DefaultSimulationFeatureExtractor().extract(state);

        assertEquals(2.5, features.get("time_seconds"), 1e-12);
        assertEquals(0.002, features.get("dt_seconds"), 1e-12);
        assertEquals(1.80, features.get("human_height_m"), 1e-12);
        assertEquals(82.0, features.get("human_mass_kg"), 1e-12);
        assertEquals(1.0, features.get("allow_hardware_joint_limit_exceedance"), 1e-12);
        assertEquals(JointType.ANKLE.ordinal(), (int) features.get("hardware_controlled_joint_index"));
        assertEquals(Math.toRadians(33), features.get("knee_angle_rad"), 1e-12);
        assertEquals(12.5, features.get("knee_motor_command_nm"), 1e-12);
        assertEquals(12.5, features.get("knee_motor_output_nm"), 1e-12);
        assertTrue(features.featureNames().get(0).equals("time_seconds"), "feature order should start with time");
        assertTrue(features.size() >= 20, "feature snapshot should expose a useful schema");
    }
}

