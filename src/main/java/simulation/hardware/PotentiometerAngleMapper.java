package simulation.hardware;

/**
 * Maps Arduino potentiometer raw samples (0..1023) to a configurable angle range.
 */
public class PotentiometerAngleMapper {

    private final int minRaw;
    private final int maxRaw;
    private final double minAngleDeg;
    private final double maxAngleDeg;

    public PotentiometerAngleMapper(int minRaw, int maxRaw, double minAngleDeg, double maxAngleDeg) {
        if (maxRaw <= minRaw) {
            throw new IllegalArgumentException("maxRaw must be greater than minRaw");
        }
        if (maxAngleDeg <= minAngleDeg) {
            throw new IllegalArgumentException("maxAngleDeg must be greater than minAngleDeg");
        }
        this.minRaw = minRaw;
        this.maxRaw = maxRaw;
        this.minAngleDeg = minAngleDeg;
        this.maxAngleDeg = maxAngleDeg;
    }

    public double mapToDegrees(int rawValue) {
        int clamped = Math.max(minRaw, Math.min(maxRaw, rawValue));
        double normalized = (clamped - minRaw) / (double) (maxRaw - minRaw);
        return minAngleDeg + normalized * (maxAngleDeg - minAngleDeg);
    }

    public double mapToRadians(int rawValue) {
        return Math.toRadians(mapToDegrees(rawValue));
    }
}

