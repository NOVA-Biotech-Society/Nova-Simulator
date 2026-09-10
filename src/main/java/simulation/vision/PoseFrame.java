package simulation.vision;

import java.util.List;

/** Immutable observation, in radians. It is never a motor command or simulation state. */
public record PoseFrame(
        long timestampMs, long frameId, int width, int height,
        double hipAngleRad, double kneeAngleRad, double ankleAngleRad,
        double hipAngularVelocityRadS, double kneeAngularVelocityRadS, double ankleAngularVelocityRadS,
        double rawHipAngleRad, double rawKneeAngleRad, double rawAnkleAngleRad,
        double hipConfidence, double kneeConfidence, double ankleConfidence,
        boolean trackingValid, boolean calibrated, String side, String facing,
        List<Landmark> landmarks, List<Landmark> worldLandmarks) {

    public static final double MIN_CONFIDENCE = 0.6;

    public PoseFrame {
        if (timestampMs <= 0 || frameId < 0 || width <= 0 || height <= 0 || width > 4096 || height > 4096)
            throw new IllegalArgumentException("Invalid pose frame metadata");
        for (double value : new double[]{hipAngleRad, kneeAngleRad, ankleAngleRad,
                hipAngularVelocityRadS, kneeAngularVelocityRadS, ankleAngularVelocityRadS,
                rawHipAngleRad, rawKneeAngleRad, rawAnkleAngleRad}) {
            if (!Double.isFinite(value) || Math.abs(value) > 100)
                throw new IllegalArgumentException("Invalid pose measurement");
        }
        for (double confidence : new double[]{hipConfidence, kneeConfidence, ankleConfidence}) {
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)
                throw new IllegalArgumentException("Invalid confidence");
        }
        if (!("LEFT".equals(side) || "RIGHT".equals(side))
                || !("LEFT".equals(facing) || "RIGHT".equals(facing)))
            throw new IllegalArgumentException("Invalid capture orientation");
        landmarks = validateLandmarks(landmarks);
        worldLandmarks = validateLandmarks(worldLandmarks);
        if (trackingValid && (landmarks.size() != 33
                || Math.min(hipConfidence, Math.min(kneeConfidence, ankleConfidence)) < MIN_CONFIDENCE))
            throw new IllegalArgumentException("Valid tracking requires visible landmarks");
        if (trackingValid && (hipAngleRad < Math.toRadians(-30) - 1e-9 || hipAngleRad > Math.toRadians(130) + 1e-9
                || kneeAngleRad < 0 || kneeAngleRad > Math.toRadians(140) + 1e-9
                || ankleAngleRad < Math.toRadians(-50) - 1e-9 || ankleAngleRad > Math.toRadians(30) + 1e-9))
            throw new IllegalArgumentException("Mapped pose exceeds NOVA limits");
    }

    public double confidence() { return Math.min(hipConfidence, Math.min(kneeConfidence, ankleConfidence)); }

    public static List<Landmark> validateLandmarks(List<Landmark> points) {
        if (points == null || (points.size() != 0 && points.size() != 33))
            throw new IllegalArgumentException("Expected zero or 33 landmarks");
        return List.copyOf(points);
    }

    public record Landmark(double x, double y, double z, double visibility, double presence) {
        public Landmark {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 10 || Math.abs(y) > 10 || Math.abs(z) > 10
                    || !Double.isFinite(visibility) || !Double.isFinite(presence)
                    || visibility < 0 || visibility > 1 || presence < 0 || presence > 1)
                throw new IllegalArgumentException("Invalid landmark");
        }
        public double confidence() { return Math.min(visibility, presence); }
    }
}
