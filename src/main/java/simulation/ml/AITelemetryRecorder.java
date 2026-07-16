package simulation.ml;

import simulation.model.SimulationState;

/**
 * Records a post-step telemetry row for offline training and audits.
 */
public interface AITelemetryRecorder {

    void recordStep(SimulationState state);
}

