package simulation.controller;

import simulation.ml.SimulationCommandPolicy;
import simulation.ml.SimulationFeatureExtractor;
import simulation.model.SimulationState;

import java.util.Objects;

/**
 * Bridges the simulation engine to a feature-driven policy.
 * <p>
 * This keeps the control contract stable while the policy implementation evolves
 * from scripted logic to a trained Tribuo model.
 * </p>
 */
public class FeatureBackedExoController implements ExoController {

    private final SimulationFeatureExtractor featureExtractor;
    private final SimulationCommandPolicy policy;

    public FeatureBackedExoController(SimulationFeatureExtractor featureExtractor, SimulationCommandPolicy policy) {
        this.featureExtractor = Objects.requireNonNull(featureExtractor, "featureExtractor");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public void reset() {
        policy.reset();
    }

    @Override
    public MotorCommands computeCommands(SimulationState state, double time) {
        return policy.computeCommands(featureExtractor.extract(state));
    }

    @Override
    public String getName() {
        return policy.getName();
    }

    public SimulationFeatureExtractor getFeatureExtractor() {
        return featureExtractor;
    }

    public SimulationCommandPolicy getPolicy() {
        return policy;
    }
}

