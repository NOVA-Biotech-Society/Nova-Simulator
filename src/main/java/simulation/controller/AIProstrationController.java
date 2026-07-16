package simulation.controller;

import simulation.ml.AIControlDiagnostics;
import simulation.ml.AIControlDiagnosticsProvider;
import simulation.ml.AIInferenceEngine;
import simulation.ml.AIOutput;
import simulation.ml.AITelemetryRecorder;
import simulation.ml.FeatureBuffer;
import simulation.ml.HeuristicTcnInferenceEngine;
import simulation.ml.JointTargets;
import simulation.ml.PDController;
import simulation.ml.ReferenceTrajectory;
import simulation.ml.SafetyLimiter;
import simulation.ml.SensorWindow;
import simulation.model.HumanModel;
import simulation.model.SimulationState;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;

/**
 * AI-assisted controller for prostration movement prediction and safe assistance.
 * <p>
 * The temporal model predicts movement phase and progression; the PD layer generates torques;
 * the safety limiter clamps everything before the engine applies it.
 * </p>
 */
public class AIProstrationController implements ExoController, AIControlDiagnosticsProvider, AITelemetryRecorder {

    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.75;
    private static final int DEFAULT_WINDOW_SIZE = 100;
    private static final double DEFAULT_MAX_DELTA_TORQUE_PER_SECOND = 250.0;

    private final FeatureBuffer featureBuffer;
    private final AIInferenceEngine inferenceEngine;
    private final ReferenceTrajectory referenceTrajectory;
    private final PDController pdController;
    private final SafetyLimiter safetyLimiter;
    private final double confidenceThreshold;
    private final Path telemetryPath;
    private final Path modelPath;
    private final TelemetryCsvWriter telemetryWriter;

    private volatile AIOutput lastOutput = AIOutput.safeHold();
    private volatile MotorCommands lastRawCommands = MotorCommands.ZERO;
    private volatile MotorCommands lastSafeCommands = MotorCommands.ZERO;
    private volatile long lastInferenceMicros;
    private volatile String lastSafetyMode = "HOLD";
    private volatile String lastFallbackReason = "Model not evaluated yet";
    private volatile int lastSafetyViolationCount;

    public AIProstrationController(Path modelPath) {
        this(modelPath, DEFAULT_WINDOW_SIZE, DEFAULT_CONFIDENCE_THRESHOLD, null);
    }

    public AIProstrationController(Path modelPath, int windowSize, double confidenceThreshold, Path telemetryPath) {
        this(modelPath,
                new FeatureBuffer(windowSize),
                new HeuristicTcnInferenceEngine(modelPath),
                new ReferenceTrajectory(),
                new PDController(),
                new SafetyLimiter(DEFAULT_MAX_DELTA_TORQUE_PER_SECOND),
                confidenceThreshold,
                telemetryPath);
    }

    public AIProstrationController(Path modelPath,
                                   FeatureBuffer featureBuffer,
                                   AIInferenceEngine inferenceEngine,
                                   ReferenceTrajectory referenceTrajectory,
                                   PDController pdController,
                                   SafetyLimiter safetyLimiter,
                                   double confidenceThreshold,
                                   Path telemetryPath) {
        this.modelPath = modelPath;
        this.featureBuffer = Objects.requireNonNull(featureBuffer, "featureBuffer");
        this.inferenceEngine = Objects.requireNonNull(inferenceEngine, "inferenceEngine");
        this.referenceTrajectory = Objects.requireNonNull(referenceTrajectory, "referenceTrajectory");
        this.pdController = Objects.requireNonNull(pdController, "pdController");
        this.safetyLimiter = Objects.requireNonNull(safetyLimiter, "safetyLimiter");
        this.confidenceThreshold = confidenceThreshold;
        this.telemetryPath = telemetryPath;
        this.telemetryWriter = telemetryPath == null ? null : new TelemetryCsvWriter(telemetryPath);
    }

    @Override
    public synchronized void reset() {
        featureBuffer.reset();
        inferenceEngine.reset();
        safetyLimiter.reset();
        lastOutput = AIOutput.safeHold();
        lastRawCommands = MotorCommands.ZERO;
        lastSafeCommands = MotorCommands.ZERO;
        lastInferenceMicros = 0L;
        lastSafetyMode = "HOLD";
        lastFallbackReason = "Reset";
        lastSafetyViolationCount = 0;
        if (telemetryWriter != null) {
            telemetryWriter.flushQuietly();
        }
    }

    @Override
    public synchronized MotorCommands computeCommands(SimulationState state, double time) {
        SensorWindow window = featureBuffer.updateAndGet(state);
        long start = System.nanoTime();
        AIOutput output = inferenceEngine.predict(window);
        lastInferenceMicros = Math.max(0L, (System.nanoTime() - start) / 1_000L);
        lastOutput = output;
        lastSafetyViolationCount = state.getSafetyViolations().size();

        if (!inferenceEngine.isReady()) {
            lastFallbackReason = modelPath == null ? "Model path not configured" : "Trained model not present; heuristic baseline active";
        } else {
            lastFallbackReason = "Trained model ready";
        }

        if (!state.getSafetyViolations().isEmpty()) {
            lastSafetyMode = "SAFETY_HOLD";
            lastRawCommands = MotorCommands.ZERO;
            lastSafeCommands = safetyLimiter.clamp(MotorCommands.ZERO, state);
            lastFallbackReason = "Latched due to safety violation";
            return lastSafeCommands;
        }

        if (!Double.isFinite(output.confidence()) || output.confidence() < confidenceThreshold) {
            lastSafetyMode = "CONFIDENCE_HOLD";
            lastRawCommands = MotorCommands.ZERO;
            lastSafeCommands = safetyLimiter.clamp(MotorCommands.ZERO, state);
            lastFallbackReason = String.format(Locale.ROOT, "Confidence %.3f below threshold %.3f", output.confidence(), confidenceThreshold);
            return lastSafeCommands;
        }

        JointTargets targets = referenceTrajectory.fromPhase(output.currentKeyframe(), output.progress());
        lastRawCommands = pdController.computeCommands(state, targets);
        lastSafeCommands = safetyLimiter.clamp(lastRawCommands, state);
        lastSafetyMode = safetyLimiter.mode();
        lastFallbackReason = safetyLimiter.isLatched() ? "Safety limiter latched" : "Assisted mode active";
        return lastSafeCommands;
    }

    @Override
    public String getName() {
        return "AI Prostration Controller";
    }

    @Override
    public synchronized AIControlDiagnostics getDiagnostics() {
        AIOutput output = lastOutput == null ? AIOutput.safeHold() : lastOutput;
        return new AIControlDiagnostics(
                output.currentKeyframe(),
                output.nextKeyframe(),
                output.progress(),
                output.confidence(),
                lastSafetyMode,
                inferenceEngine.isReady(),
                modelPath,
                lastInferenceMicros,
                lastSafetyViolationCount,
                lastFallbackReason
        );
    }

    @Override
    public synchronized void recordStep(SimulationState state) {
        if (telemetryWriter == null) {
            return;
        }
        lastSafetyViolationCount = state.getSafetyViolations().size();
        telemetryWriter.write(state, lastOutput, lastRawCommands, lastSafeCommands, lastSafetyMode, lastFallbackReason);
    }

    public Path getTelemetryPath() {
        return telemetryPath;
    }

    public Path getModelPath() {
        return modelPath;
    }

    public boolean isModelReady() {
        return inferenceEngine.isReady();
    }

    public AIOutput getLastOutput() {
        return lastOutput;
    }

    public MotorCommands getLastSafeCommands() {
        return lastSafeCommands;
    }

    private static final class TelemetryCsvWriter {
        private final Path path;
        private BufferedWriter writer;
        private boolean headerWritten;

        private TelemetryCsvWriter(Path path) {
            this.path = path.toAbsolutePath();
        }

        private synchronized void write(SimulationState state, AIOutput output, MotorCommands raw, MotorCommands safe,
                                        String safetyMode, String fallbackReason) {
            try {
                ensureOpen();
                if (!headerWritten) {
                    writer.write(String.join(",",
                            "timestamp",
                            "imu_ax", "imu_ay", "imu_az",
                            "imu_gx", "imu_gy", "imu_gz",
                            "hip_angle", "knee_angle", "ankle_angle",
                            "hip_omega", "knee_omega", "ankle_omega",
                            "current_keyframe_label", "next_keyframe_label", "delta_keyframe_label",
                            "progress", "confidence",
                            "hip_target", "knee_target", "ankle_target",
                            "hip_torque", "knee_torque", "ankle_torque",
                            "raw_hip_torque", "raw_knee_torque", "raw_ankle_torque",
                            "safety_mode", "safety_violation", "fallback_reason"
                    ));
                    writer.newLine();
                    headerWritten = true;
                }

                HumanModel human = state.getHumanModel();
                var exo = state.getExoskeletonModel();
                var imu = exo.getSensors().isEmpty() ? null : exo.getSensors().get(0);
                double timestamp = state.getTime();
                double imuAx = imu == null ? 0.0 : imu.getAccelX();
                double imuAy = imu == null ? 0.0 : imu.getAccelY();
                double imuAz = imu == null ? 0.0 : imu.getAccelZ();
                double imuGx = imu == null ? 0.0 : imu.getGyroX();
                double imuGy = imu == null ? 0.0 : imu.getGyroY();
                double imuGz = imu == null ? 0.0 : imu.getGyroZ();
                double hipAngle = human.getHipJoint().getAngle();
                double kneeAngle = human.getKneeJoint().getAngle();
                double ankleAngle = human.getAnkleJoint().getAngle();
                double hipOmega = human.getHipJoint().getAngularVelocity();
                double kneeOmega = human.getKneeJoint().getAngularVelocity();
                double ankleOmega = human.getAnkleJoint().getAngularVelocity();
                writer.write(String.format(Locale.ROOT,
                        "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%s,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%s,%s",
                        timestamp,
                        imuAx, imuAy, imuAz,
                        imuGx, imuGy, imuGz,
                        hipAngle, kneeAngle, ankleAngle,
                        hipOmega, kneeOmega, ankleOmega,
                        safeKeyframe(output.currentKeyframe()),
                        safeKeyframe(output.nextKeyframe()),
                        deltaLabel(safeKeyframe(output.currentKeyframe()), safeKeyframe(output.nextKeyframe())),
                        output.progress(),
                        output.confidence(),
                        output.hipTarget(), output.kneeTarget(), output.ankleTarget(),
                        safe.hipTorque(), safe.kneeTorque(), safe.ankleTorque(),
                        raw.hipTorque(), raw.kneeTorque(), raw.ankleTorque(),
                        csv(safetyMode),
                        state.getSafetyViolations().isEmpty() ? "0" : "1",
                        csv(fallbackReason)
                ));
                writer.newLine();
                writer.flush();
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to write AI telemetry to " + path, ex);
            }
        }

        private synchronized void flushQuietly() {
            if (writer == null) {
                return;
            }
            try {
                writer.flush();
            } catch (IOException ignored) {
                // Best-effort flush.
            }
        }

        private void ensureOpen() throws IOException {
            if (writer != null) {
                return;
            }
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writer = Files.newBufferedWriter(path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE);
        }


        private String safeKeyframe(Object keyframe) {
            return keyframe == null ? "UNKNOWN" : keyframe.toString();
        }

        private String deltaLabel(String current, String next) {
            if (current == null || next == null) {
                return "0";
            }
            if ("RETURN_STANDING".equals(current) && "STANDING".equals(next)) {
                return "0";
            }
            return switch (current) {
                case "STANDING", "DESCENDING" -> "+1";
                case "PROSTRATION", "ASCENDING" -> "-1";
                case "RETURN_STANDING" -> "+2";
                default -> "0";
            };
        }

        private String csv(String value) {
            String text = value == null ? "" : value.replace("\"", "\"\"");
            if (text.contains(",") || text.contains("\"")) {
                return '"' + text + '"';
            }
            return text;
        }
    }
}

