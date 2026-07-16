package simulation.ml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Baseline temporal inference engine used until a trained TCN is exported.
 * <p>
 * It is deliberately conservative: it estimates the current phase from the latest
 * temporal context, produces smooth progression, and lowers confidence when the
 * motion is ambiguous or the window is too short.
 * </p>
 */
public class HeuristicTcnInferenceEngine implements AIInferenceEngine {

    private final Path modelPath;
    private final ReferenceTrajectory referenceTrajectory = new ReferenceTrajectory();

    public HeuristicTcnInferenceEngine(Path modelPath) {
        this.modelPath = modelPath;
    }

    @Override
    public AIOutput predict(SensorWindow window) {
        if (window == null || window.size() == 0) {
            return AIOutput.safeHold();
        }

        SensorSnapshot latest = window.latest();
        List<SensorSnapshot> samples = window.samples();
        SensorSnapshot previous = samples.size() >= 2 ? samples.get(samples.size() - 2) : latest;

        double kneeAngle = latest.kneeAngle();
        double hipAngle = latest.hipAngle();
        double ankleAngle = latest.ankleAngle();
        double kneeOmega = latest.kneeOmega();
        double hipOmega = latest.hipOmega();
        double ankleOmega = latest.ankleOmega();

        Keyframe current = estimateCurrentKeyframe(kneeAngle, hipAngle, kneeOmega, hipOmega);
        Keyframe next = current.next();
        double progress = estimateProgress(current, kneeAngle, hipAngle, ankleAngle, kneeOmega, hipOmega, ankleOmega);
        double confidence = estimateConfidence(window, previous, latest, current, progress);

        JointTargets targets = referenceTrajectory.fromPhase(current, progress);
        return new AIOutput(current, next, progress, confidence,
                targets.hipAngle(), targets.kneeAngle(), targets.ankleAngle());
    }

    @Override
    public String getName() {
        return "Heuristic TCN Baseline";
    }

    @Override
    public boolean isReady() {
        return modelPath != null && Files.exists(modelPath);
    }

    public Path getModelPath() {
        return modelPath;
    }

    private Keyframe estimateCurrentKeyframe(double kneeAngle, double hipAngle, double kneeOmega, double hipOmega) {
        double kneeDeg = Math.toDegrees(kneeAngle);
        double hipDeg = Math.toDegrees(hipAngle);
        double movement = Math.abs(kneeOmega) + Math.abs(hipOmega);

        if (kneeDeg < 15.0 && hipDeg < 10.0) {
            return movement < 0.15 ? Keyframe.STANDING : (hipOmega > 0.0 || kneeOmega > 0.0 ? Keyframe.DESCENDING : Keyframe.RETURN_STANDING);
        }
        if (kneeDeg > 110.0 || hipDeg > 85.0) {
            return movement < 0.2 ? Keyframe.PROSTRATION : (kneeOmega < 0.0 || hipOmega < 0.0 ? Keyframe.ASCENDING : Keyframe.PROSTRATION);
        }
        if (kneeOmega < -0.05 || hipOmega < -0.05) {
            return Keyframe.ASCENDING;
        }
        if (kneeOmega > 0.05 || hipOmega > 0.05) {
            return Keyframe.DESCENDING;
        }
        return Keyframe.DESCENDING;
    }

    private double estimateProgress(Keyframe current, double kneeAngle, double hipAngle, double ankleAngle,
                                    double kneeOmega, double hipOmega, double ankleOmega) {
        double phaseProgress;
        switch (current) {
            case STANDING -> phaseProgress = clamp01((Math.abs(kneeAngle) + Math.abs(hipAngle)) / Math.toRadians(20.0));
            case DESCENDING -> phaseProgress = clamp01(Math.max(
                    Math.toDegrees(kneeAngle) / 140.0,
                    Math.max(Math.toDegrees(hipAngle) / 110.0, Math.abs(Math.toDegrees(ankleAngle)) / 25.0)));
            case PROSTRATION -> phaseProgress = clamp01((Math.toDegrees(kneeAngle) - 110.0 + Math.abs(Math.toDegrees(ankleAngle)) * 0.1) / 30.0);
            case ASCENDING -> phaseProgress = clamp01(1.0 - Math.max(
                    Math.toDegrees(kneeAngle) / 140.0,
                    Math.max(Math.toDegrees(hipAngle) / 110.0, Math.abs(Math.toDegrees(ankleAngle)) / 25.0)));
            case RETURN_STANDING -> phaseProgress = clamp01(1.0 - (Math.abs(kneeAngle) + Math.abs(hipAngle)) / Math.toRadians(30.0));
            default -> phaseProgress = 0.0;
        }
        double motionPenalty = clamp01((Math.abs(kneeOmega) + Math.abs(hipOmega) + Math.abs(ankleOmega)) / 8.0);
        return clamp01(0.85 * phaseProgress + 0.15 * (1.0 - motionPenalty));
    }

    private double estimateConfidence(SensorWindow window, SensorSnapshot previous, SensorSnapshot latest,
                                      Keyframe current, double progress) {
        double windowConfidence = clamp01(window.size() / (double) window.maxSamples());
        double motionDelta = Math.abs(latest.kneeAngle() - previous.kneeAngle())
                + Math.abs(latest.hipAngle() - previous.hipAngle())
                + Math.abs(latest.ankleAngle() - previous.ankleAngle());
        double smoothness = 1.0 - clamp01(motionDelta / Math.toRadians(45.0));
        double phaseBias = switch (current) {
            case STANDING, RETURN_STANDING -> 0.9;
            case DESCENDING, ASCENDING -> 0.85;
            case PROSTRATION -> 0.95;
        };
        return clamp01(0.35 * windowConfidence + 0.35 * smoothness + 0.30 * phaseBias - 0.10 * Math.abs(0.5 - progress));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

