package simulation.ml;

import java.nio.file.Path;

/**
 * Read-only status snapshot for the diagnostic UI.
 */
public record AIControlDiagnostics(
        Keyframe currentKeyframe,
        Keyframe nextKeyframe,
        double progress,
        double confidence,
        String safetyMode,
        boolean modelReady,
        Path modelPath,
        long lastInferenceMicros,
        int safetyViolationCount,
        String fallbackReason
) {
}

