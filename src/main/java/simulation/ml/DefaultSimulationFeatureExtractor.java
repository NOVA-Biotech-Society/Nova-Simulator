package simulation.ml;

import simulation.model.ExoskeletonModel;
import simulation.model.HumanModel;
import simulation.model.Joint;
import simulation.model.Motor;
import simulation.model.SimulationState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default feature extractor for future ML training and inference.
 * <p>
 * This keeps the schema small, stable, and physically meaningful:
 * time, body state, motor feedback, and a few simulation flags.
 * </p>
 */
public class DefaultSimulationFeatureExtractor implements SimulationFeatureExtractor {

    @Override
    public SimulationFeatureVector extract(SimulationState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }

        Map<String, Double> features = new LinkedHashMap<>();
        HumanModel human = state.getHumanModel();
        ExoskeletonModel exo = state.getExoskeletonModel();

        put(features, "time_seconds", state.getTime());
        put(features, "dt_seconds", state.getDt());
        put(features, "human_height_m", human.getHeight());
        put(features, "human_mass_kg", human.getTotalMass());
        put(features, "hip_anchor_x_m", human.getHipAnchorX());
        put(features, "hip_anchor_y_m", human.getHipAnchorY());
        put(features, "safety_violation_count", state.getSafetyViolations().size());
        put(features, "allow_hardware_joint_limit_exceedance", state.isAllowHardwareJointLimitExceedance() ? 1.0 : 0.0);
        put(features, "hardware_controlled_joint_index", state.getHardwareControlledJoint().ordinal());
        put(features, "exo_sensor_count", exo.getSensors().size());
        put(features, "exo_max_torque_nm", exo.getMotorMaxTorque());
        put(features, "exo_max_power_w", exo.getMotorMaxPower());

        addJointFeatures(features, "hip", human.getHipJoint(), exo.getHipMotor());
        addJointFeatures(features, "knee", human.getKneeJoint(), exo.getKneeMotor());
        addJointFeatures(features, "ankle", human.getAnkleJoint(), exo.getAnkleMotor());

        return SimulationFeatureVector.of(features);
    }

    private void addJointFeatures(Map<String, Double> features, String prefix, Joint joint, Motor motor) {
        put(features, prefix + "_angle_rad", joint.getAngle());
        put(features, prefix + "_angular_velocity_rad_per_s", joint.getAngularVelocity());
        put(features, prefix + "_min_angle_rad", joint.getMinAngle());
        put(features, prefix + "_max_angle_rad", joint.getMaxAngle());
        put(features, prefix + "_motor_command_nm", motor.getCommandTorque());
        put(features, prefix + "_motor_output_nm", motor.getOutputTorque());
        put(features, prefix + "_motor_max_torque_nm", motor.getMaxTorque());
        put(features, prefix + "_motor_max_power_w", motor.getMaxPower());
    }

    private void put(Map<String, Double> features, String name, double value) {
        features.put(name, value);
    }
}

