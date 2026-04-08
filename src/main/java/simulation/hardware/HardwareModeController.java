package simulation.hardware;

import javafx.application.Platform;
import simulation.controller.ExoController;
import simulation.model.Joint;
import simulation.model.JointType;
import simulation.physics.SimulationEngine;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Coordinates mode switching, serial lifecycle, value mapping, and joint target updates.
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
            engine.getState().setAllowHardwareJointLimitExceedance(false);
            hardwareKneeController.setUseDelegateCommands(true);
            engine.setController(defaultController);
            publishStatus("Hardware: Default mode active");
            return;
        }

        engine.getState().setAllowHardwareJointLimitExceedance(false);
        hardwareKneeController.setUseDelegateCommands(false);
        engine.setController(hardwareKneeController);
        updateControlledJoint(engine.getState().getHardwareControlledJoint());
        publishStatus("Hardware: Hardware mode active (no scripted motion). Select joint/port then connect");
    }

    public void updateControlledJoint(JointType selectedJoint) {
        JointType jointToControl = selectedJoint != null ? selectedJoint : JointType.KNEE;
        engine.getState().setHardwareControlledJoint(jointToControl);
        hardwareKneeController.setControlledJointType(jointToControl);

        String suffix = serialService.isConnected() ? " (live)" : "";
        publishStatus("Hardware: Controlling joint = " + jointToControl + suffix);
    }

    public void connect(String systemPortName, JointType selectedJoint, double minAngleDeg, double maxAngleDeg) {
        if (mode != SimulationMode.HARDWARE) {
            publishStatus("Hardware: Switch to Hardware mode before connecting");
            return;
        }
        if (systemPortName == null || systemPortName.isBlank()) {
            publishStatus("Hardware: Select a serial port first");
            return;
        }

        JointType jointToControl = selectedJoint != null ? selectedJoint : JointType.KNEE;
        mapper = new PotentiometerAngleMapper(0, 1023, minAngleDeg, maxAngleDeg);
        smoother.reset();

        updateControlledJoint(jointToControl);
        engine.getState().setAllowHardwareJointLimitExceedance(true);
        hardwareKneeController.clearTargetJointAngle();

        try {
            serialService.connect(systemPortName, DEFAULT_BAUD_RATE, new ArduinoSerialService.Listener() {
                @Override
                public void onConnected(String portName) {
                    publishConnection(true);
                    publishStatus("Hardware: Connected to " + portName + " @9600 (joint=" + jointToControl + ")");
                }

                @Override
                public void onDisconnected() {
                    publishConnection(false);
                    hardwareKneeController.clearTargetJointAngle();
                    publishStatus("Hardware: Disconnected");
                }

                @Override
                public void onNumericValue(int value) {
                    double mappedRadians = mapper.mapToRadians(value);
                    double smoothRadians = smoother.addSample(mappedRadians);
                    hardwareKneeController.setTargetJointAngleRadians(smoothRadians);

                    Joint selected = getJoint(jointToControl);
                    StringBuilder status = new StringBuilder(
                            String.format("Hardware: %s raw=%d target=%.1f deg", jointToControl, value, Math.toDegrees(smoothRadians))
                    );

                    if (selected != null) {
                        double actualDeg = Math.toDegrees(selected.getAngle());
                        status.append(String.format(" actual=%.1f deg", actualDeg));

                        if (smoothRadians > selected.getMaxAngle() || smoothRadians < selected.getMinAngle()) {
                            status.append(" | WARNING target exceeds joint limits");
                        }
                        if (selected.getAngle() > selected.getMaxAngle() || selected.getAngle() < selected.getMinAngle()) {
                            status.append(" | WARNING actual angle exceeded");
                        }
                    }

                    publishStatus(status.toString());
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
            hardwareKneeController.clearTargetJointAngle();
            publishStatus("Hardware error: " + ex.getMessage());
        }
    }

    public void disconnect() {
        serialService.disconnect();
        smoother.reset();
        hardwareKneeController.clearTargetJointAngle();
        engine.getState().setAllowHardwareJointLimitExceedance(false);
        publishConnection(false);
    }

    public void shutdown() {
        disconnect();
    }

    private Joint getJoint(JointType jointType) {
        return switch (jointType) {
            case HIP -> engine.getState().getHumanModel().getHipJoint();
            case KNEE -> engine.getState().getHumanModel().getKneeJoint();
            case ANKLE -> engine.getState().getHumanModel().getAnkleJoint();
        };
    }

    private void publishStatus(String message) {
        Platform.runLater(() -> statusConsumer.accept(message));
    }

    private void publishConnection(boolean connected) {
        Platform.runLater(() -> connectionConsumer.accept(connected));
    }
}
