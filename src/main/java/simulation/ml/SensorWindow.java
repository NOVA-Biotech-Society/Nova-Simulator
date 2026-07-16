package simulation.ml;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Rolling temporal window of {@link SensorSnapshot} samples.
 */
public class SensorWindow {

    public static final int FEATURE_COUNT = 13;

    private final int maxSamples;
    private final Deque<SensorSnapshot> samples = new ArrayDeque<>();

    public SensorWindow(int maxSamples) {
        if (maxSamples <= 0) {
            throw new IllegalArgumentException("maxSamples must be positive");
        }
        this.maxSamples = maxSamples;
    }

    public void add(SensorSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (samples.size() == maxSamples) {
            samples.removeFirst();
        }
        samples.addLast(snapshot);
    }

    public int size() {
        return samples.size();
    }

    public int maxSamples() {
        return maxSamples;
    }

    public boolean isReady() {
        return samples.size() >= maxSamples;
    }

    public SensorSnapshot latest() {
        return samples.peekLast();
    }

    public List<SensorSnapshot> samples() {
        return Collections.unmodifiableList(new ArrayList<>(samples));
    }

    public void clear() {
        samples.clear();
    }

    public float[][] toTensor() {
        float[][] tensor = new float[maxSamples][FEATURE_COUNT];
        List<SensorSnapshot> ordered = samples();
        int offset = Math.max(0, maxSamples - ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            SensorSnapshot sample = ordered.get(i);
            int row = offset + i;
            tensor[row][0] = (float) sample.timestamp();
            tensor[row][1] = (float) sample.imuAx();
            tensor[row][2] = (float) sample.imuAy();
            tensor[row][3] = (float) sample.imuAz();
            tensor[row][4] = (float) sample.imuGx();
            tensor[row][5] = (float) sample.imuGy();
            tensor[row][6] = (float) sample.imuGz();
            tensor[row][7] = (float) sample.hipAngle();
            tensor[row][8] = (float) sample.kneeAngle();
            tensor[row][9] = (float) sample.ankleAngle();
            tensor[row][10] = (float) sample.hipOmega();
            tensor[row][11] = (float) sample.kneeOmega();
            tensor[row][12] = (float) sample.ankleOmega();
        }
        return tensor;
    }
}

