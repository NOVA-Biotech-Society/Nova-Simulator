package simulation.ml;

import simulation.controller.MotorCommands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Tribuo-facing policy placeholder.
 * <p>
 * This keeps the external surface ready for a trained Tribuo model while the
 * project is still in the data-collection and training setup phase. For now it
 * safely falls back to the scripted reference policy, so the simulator remains
 * usable without a trained model file.
 * </p>
 * <p>
 * When training is ready, replace the fallback path here with real Tribuo model
 * loading and inference, without changing the rest of the simulator wiring.
 * </p>
 */
public class TribuoControlPolicy implements SimulationCommandPolicy {

    private final Path modelPath;
    private final ScriptedReferencePolicy fallbackPolicy;

    public TribuoControlPolicy(Path modelPath) {
        this(modelPath, new ScriptedReferencePolicy());
    }

    public TribuoControlPolicy(Path modelPath, ScriptedReferencePolicy fallbackPolicy) {
        this.modelPath = modelPath;
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
    }

    @Override
    public MotorCommands computeCommands(SimulationFeatureVector features) {
        // Future Tribuo inference hook:
        // 1) load a trained Tribuo model from modelPath
        // 2) convert the feature vector into a Tribuo Example
        // 3) predict motor torques
        // For now, fall back to the reference scripted policy.
        return fallbackPolicy.computeCommands(features);
    }

    @Override
    public String getName() {
        return "Tribuo Control Policy Skeleton";
    }

    public Path getModelPath() {
        return modelPath;
    }

    public boolean isModelConfigured() {
        return modelPath != null;
    }

    public boolean isModelPresent() {
        return modelPath != null && Files.exists(modelPath);
    }

    public String describe() {
        if (modelPath == null) {
            return "Tribuo model path not configured yet";
        }
        return "Tribuo model path: " + modelPath.toAbsolutePath();
    }
}

