package simulation.ml;

import simulation.controller.MotorCommands;

/**
 * Produces motor commands from a feature snapshot.
 * <p>
 * The simulator can keep this interface stable while the implementation evolves
 * from scripted rules to trained Tribuo models.
 * </p>
 */
public interface SimulationCommandPolicy {

    MotorCommands computeCommands(SimulationFeatureVector features);

    String getName();

    default void reset() {
        // Stateless by default.
    }
}

