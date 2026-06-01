package simulation.hardware;

/**
 * Lightweight EMA smoother to reduce jitter from noisy hardware inputs.
 */
public class ExponentialSmoother {

    private final double alpha;
    private Double current;

    public ExponentialSmoother(double alpha) {
        if (alpha <= 0 || alpha >= 1.0) {
            throw new IllegalArgumentException("alpha must be in (0, 1)");
        }
        this.alpha = alpha;
    }

    public synchronized double addSample(double sample) {
        if (current == null) {
            current = sample;
        } else {
            current = alpha * sample + (1.0 - alpha) * current;
        }
        return current;
    }

    public synchronized void reset() {
        current = null;
    }
}
