package simulation.ml;

/**
 * Temporal inference backend for movement phase estimation.
 */
public interface AIInferenceEngine {

    AIOutput predict(SensorWindow window);

    String getName();

    default boolean isReady() {
        return true;
    }

    default void reset() {
        // Stateless by default.
    }
}

