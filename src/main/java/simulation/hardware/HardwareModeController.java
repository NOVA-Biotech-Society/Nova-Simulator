package simulation.hardware;

import javafx.application.Platform;
import simulation.controller.ExoController;
import simulation.physics.SimulationEngine;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Coordinates mode switching, serial lifecycle, value mapping, and knee target updates.
 */
public class HardwareModeController {

    private static final int DEFAULT_BAUD_RATE = 9600;

    private final SimulationEngine engine;
    private final ExoController defaultController;
    private final HardwareKneeController hardwareKneeController;
    private final ArduinoSerialService serialService;
    private final ExponentialSmoother smoother;

    private Consumer<String> statusConsumer = ignored -> { };
    private Consumer<Boolean> connectionConsumer = ignored -> { };

    private volatile SimulationMode mode = SimulationMode.DEFAULT;
    private volatile PotentiometerAngleMapper mapper = new PotentiometerAngleMapper(0, 1023, 0.0, 120.0);

    public HardwareModeController(SimulationEngine engine, ExoController defaultController) {
        this.engine = engine;
        this.defaultController = defaultController;
        this.hardwareKneeController = new HardwareKneeController(defaultController);
        this.serialService = new ArduinoSerialService();
        this.smoother = new ExponentialSmoother(0.20);
    }

    public void setOnStatusMessage(Consumer<String> statusConsumer) {
        this.statusConsumer = statusConsumer != null ? statusConsumer : ignored -> { };
    }

    public void setOnConnectionChanged(Consumer<Boolean> connectionConsumer) {
        this.connectionConsumer = connectionConsumer != null ? connectionConsumer : ignored -> { };
    }

    public List<String> listPorts() {
        if (!isSerialRuntimeSupported()) {
            publishStatus(unsupportedRuntimeMessage());
            return Collections.emptyList();
        }

        List<String> ports = serialService.listAvailablePorts();
        if (ports.isEmpty()) {
            publishStatus("Hardware: No serial ports found");
            return Collections.emptyList();
        }
        publishStatus("Hardware: Found " + ports.size() + " serial port(s)");
        return ports;
    }

    public void setMode(SimulationMode mode) {
        this.mode = mode;
        if (mode == SimulationMode.DEFAULT) {
            disconnect();
            engine.setController(defaultController);
            publishStatus("Hardware: Default mode active");
            return;
        }

        engine.setController(hardwareKneeController);
        publishStatus("Hardware: Hardware mode active (select a port and connect)");
    }

    public void connect(String systemPortName, double minAngleDeg, double maxAngleDeg) {
        if (mode != SimulationMode.HARDWARE) {
            publishStatus("Hardware: Switch to Hardware mode before connecting");
            return;
        }
        if (!isSerialRuntimeSupported()) {
            publishStatus(unsupportedRuntimeMessage());
            return;
        }
        if (systemPortName == null || systemPortName.isBlank()) {
            publishStatus("Hardware: Select a serial port first");
            return;
        }

        mapper = new PotentiometerAngleMapper(0, 1023, minAngleDeg, maxAngleDeg);
        smoother.reset();
        hardwareKneeController.clearTargetKneeAngle();

        try {
            serialService.connect(systemPortName, DEFAULT_BAUD_RATE, new ArduinoSerialService.Listener() {
                @Override
                public void onConnected(String portName) {
                    publishConnection(true);
                    publishStatus("Hardware: Connected to " + portName + " @9600");
                }

                @Override
                public void onDisconnected() {
                    publishConnection(false);
                    hardwareKneeController.clearTargetKneeAngle();
                    publishStatus("Hardware: Disconnected");
                }

                @Override
                public void onNumericValue(int value) {
                    double mappedRadians = mapper.mapToRadians(value);
                    double smoothRadians = smoother.addSample(mappedRadians);
                    hardwareKneeController.setTargetKneeAngleRadians(smoothRadians);

                    double angleDeg = Math.toDegrees(smoothRadians);
                    publishStatus(String.format("Hardware: raw=%d angle=%.1f deg", value, angleDeg));
                }

                @Override
                public void onInfo(String message) {
                    publishStatus("Hardware: " + message);
                }

                @Override
                public void onError(String message, Exception exception) {
                    publishStatus("Hardware error: " + message);
                }
            });
        } catch (Exception ex) {
            publishConnection(false);
            hardwareKneeController.clearTargetKneeAngle();
            publishStatus("Hardware error: " + ex.getMessage());
        }
    }

    public void disconnect() {
        serialService.disconnect();
        smoother.reset();
        hardwareKneeController.clearTargetKneeAngle();
        publishConnection(false);
    }

    public void shutdown() {
        disconnect();
    }

    private void publishStatus(String message) {
        Platform.runLater(() -> statusConsumer.accept(message));
    }

    private void publishConnection(boolean connected) {
        Platform.runLater(() -> connectionConsumer.accept(connected));
    }

    private boolean isSerialRuntimeSupported() {
        // Windows ARM64 + x86 JVM emulation is a known unstable combination for native serial bindings.
        String jvmArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String envArch = System.getenv().getOrDefault("PROCESSOR_ARCHITECTURE", "").toLowerCase(Locale.ROOT);
        String envArchWow64 = System.getenv().getOrDefault("PROCESSOR_ARCHITEW6432", "").toLowerCase(Locale.ROOT);

        boolean isWindows = osName.contains("win");
        boolean jvmIsX86 = "x86".equals(jvmArch) || "i386".equals(jvmArch);
        boolean osIsArm64 = envArch.contains("arm64") || envArchWow64.contains("arm64");

        return !(isWindows && jvmIsX86 && osIsArm64);
    }

    private String unsupportedRuntimeMessage() {
        return "Hardware: unsupported runtime (x86 JVM on Windows ARM64). Use ARM64 or x64 Liberica 21 in IntelliJ SDK/Runner.";
    }
}
