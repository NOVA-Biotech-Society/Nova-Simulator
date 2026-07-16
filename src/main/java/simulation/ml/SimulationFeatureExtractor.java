package simulation.ml;

import simulation.model.SimulationState;

/**
 * Converts a live simulation state into an ordered feature snapshot.
 */
@FunctionalInterface
public interface SimulationFeatureExtractor {

    SimulationFeatureVector extract(SimulationState state);
}

