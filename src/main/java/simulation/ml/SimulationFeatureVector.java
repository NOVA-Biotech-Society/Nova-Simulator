package simulation.ml;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordered, immutable feature snapshot extracted from a simulation state.
 * <p>
 * The linked hash map preserves feature ordering so downstream dataset writers
 * and future Tribuo adapters can rely on a stable schema.
 * </p>
 */
public record SimulationFeatureVector(Map<String, Double> features) {

    public SimulationFeatureVector {
        LinkedHashMap<String, Double> ordered = new LinkedHashMap<>();
        if (features != null) {
            ordered.putAll(features);
        }
        features = Collections.unmodifiableMap(ordered);
    }

    public static SimulationFeatureVector of(Map<String, Double> features) {
        return new SimulationFeatureVector(features);
    }

    public boolean isEmpty() {
        return features.isEmpty();
    }

    public int size() {
        return features.size();
    }

    public double get(String featureName) {
        return features.getOrDefault(featureName, 0.0);
    }

    public boolean contains(String featureName) {
        return features.containsKey(featureName);
    }

    public List<String> featureNames() {
        return List.copyOf(features.keySet());
    }

    public double[] toArray() {
        double[] values = new double[features.size()];
        int index = 0;
        for (double value : features.values()) {
            values[index++] = value;
        }
        return values;
    }
}

