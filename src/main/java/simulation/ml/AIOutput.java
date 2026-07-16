package simulation.ml;

/**
 * Multi-task inference output from the temporal model.
 */
public record AIOutput(
        Keyframe currentKeyframe,
        Keyframe nextKeyframe,
        double progress,
        double confidence,
        double hipTarget,
        double kneeTarget,
        double ankleTarget
) {

    public static AIOutput safeHold() {
        return new AIOutput(Keyframe.STANDING, Keyframe.STANDING, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}

