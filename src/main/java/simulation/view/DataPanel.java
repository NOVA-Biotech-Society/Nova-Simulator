package simulation.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import simulation.model.*;

import java.util.List;

/**
 * UI panel showing real-time numeric readouts of joint angles, motor torques,
 * sensor values, and safety warnings.
 */
public class DataPanel extends VBox {

    // Joint angle labels
    private final Label hipAngleLabel   = createValueLabel();
    private final Label kneeAngleLabel  = createValueLabel();
    private final Label ankleAngleLabel = createValueLabel();

    // Motor torque labels
    private final Label hipTorqueLabel   = createValueLabel();
    private final Label kneeTorqueLabel  = createValueLabel();
    private final Label ankleTorqueLabel = createValueLabel();

    // Joint angular velocity labels
    private final Label hipOmegaLabel   = createValueLabel();
    private final Label kneeOmegaLabel  = createValueLabel();
    private final Label ankleOmegaLabel = createValueLabel();

    // IMU sensor labels (thigh IMU)
    private final Label accelLabel = createValueLabel();
    private final Label gyroLabel  = createValueLabel();
    private final Label magLabel   = createValueLabel();

    // Safety warnings
    private final ListView<String> warningsList = new ListView<>();
    private final Button clearWarningsBtn = new Button("Clear Warnings");

    public DataPanel() {
        setSpacing(6);
        setPadding(new Insets(10));
        setPrefWidth(280);
        setStyle("-fx-background-color: #2b2b2b;");

        // --- Joint Angles ---
        TitledPane anglesPane = createSection("Joint Angles (°)", createGrid(
                "Hip:", hipAngleLabel,
                "Knee:", kneeAngleLabel,
                "Ankle:", ankleAngleLabel
        ));

        // --- Motor Torques ---
        TitledPane torquesPane = createSection("Motor Torques (N·m)", createGrid(
                "Hip:", hipTorqueLabel,
                "Knee:", kneeTorqueLabel,
                "Ankle:", ankleTorqueLabel
        ));

        // --- Angular Velocities ---
        TitledPane omegaPane = createSection("Angular Velocity (rad/s)", createGrid(
                "Hip:", hipOmegaLabel,
                "Knee:", kneeOmegaLabel,
                "Ankle:", ankleOmegaLabel
        ));

        // --- IMU Sensor ---
        TitledPane imuPane = createSection("Thigh IMU", createGrid(
                "Accel:", accelLabel,
                "Gyro:", gyroLabel,
                "Mag:", magLabel
        ));

        // --- Safety Warnings ---
        Label warningsTitle = new Label("⚠ Safety Warnings");
        warningsTitle.setStyle("-fx-text-fill: #ff5722; -fx-font-weight: bold; -fx-font-size: 13px;");
        warningsList.setPrefHeight(120);
        warningsList.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #ff5722;");
        clearWarningsBtn.setStyle("-fx-background-color: #6d4c41; -fx-text-fill: white; -fx-font-size: 11px;");

        getChildren().addAll(anglesPane, torquesPane, omegaPane, imuPane, warningsTitle, warningsList, clearWarningsBtn);
    }

    /**
     * Updates all displayed values from the simulation state.
     * Must be called on the JavaFX Application Thread.
     */
    public void update(SimulationState state) {
        HumanModel human = state.getHumanModel();
        ExoskeletonModel exo = state.getExoskeletonModel();

        // Joint angles
        hipAngleLabel.setText(fmt(Math.toDegrees(human.getThigh().getAngle())));
        kneeAngleLabel.setText(fmt(Math.toDegrees(human.getKneeJoint().getAngle())));
        ankleAngleLabel.setText(fmt(Math.toDegrees(human.getAnkleJoint().getAngle())));

        // Motor torques
        hipTorqueLabel.setText(fmt(exo.getHipMotor().getOutputTorque()));
        kneeTorqueLabel.setText(fmt(exo.getKneeMotor().getOutputTorque()));
        ankleTorqueLabel.setText(fmt(exo.getAnkleMotor().getOutputTorque()));

        // Angular velocities
        hipOmegaLabel.setText(fmt(human.getThigh().getAngularVelocity()));
        kneeOmegaLabel.setText(fmt(human.getKneeJoint().getAngularVelocity()));
        ankleOmegaLabel.setText(fmt(human.getAnkleJoint().getAngularVelocity()));

        // IMU sensor (thigh)
        List<ImuSensor> sensors = exo.getSensors();
        if (!sensors.isEmpty()) {
            ImuSensor thighIMU = sensors.get(0);
            accelLabel.setText(String.format("(%.2f, %.2f)", thighIMU.getAccelX(), thighIMU.getAccelY()));
            gyroLabel.setText(String.format("%.3f", thighIMU.getGyroZ()));
            magLabel.setText(String.format("(%.2f, %.2f)", thighIMU.getMagX(), thighIMU.getMagY()));
        }

        // Safety warnings (show last 20)
        List<SimulationState.SafetyViolation> violations = state.getSafetyViolations();
        warningsList.getItems().clear();
        if (!violations.isEmpty()) {
            int start = Math.max(0, violations.size() - 20);
            for (int i = start; i < violations.size(); i++) {
                warningsList.getItems().add(violations.get(i).toString());
            }
        }
    }

    public Button getClearWarningsButton() {
        return clearWarningsBtn;
    }

    /** Clears all displayed values. */
    public void clear() {
        hipAngleLabel.setText("0.0");
        kneeAngleLabel.setText("0.0");
        ankleAngleLabel.setText("0.0");
        hipTorqueLabel.setText("0.0");
        kneeTorqueLabel.setText("0.0");
        ankleTorqueLabel.setText("0.0");
        hipOmegaLabel.setText("0.0");
        kneeOmegaLabel.setText("0.0");
        ankleOmegaLabel.setText("0.0");
        accelLabel.setText("(0, 0)");
        gyroLabel.setText("0.0");
        magLabel.setText("(0, 0)");
        warningsList.getItems().clear();
    }

    private String fmt(double v) {
        return String.format("%.2f", v);
    }

    private Label createValueLabel() {
        Label label = new Label("0.0");
        label.setStyle("-fx-text-fill: #00e676; -fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        return label;
    }

    // Overloaded to accept Label objects for values
    private GridPane createGrid(String name1, Label val1,
                                String name2, Label val2,
                                String name3, Label val3) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(4);
        grid.setPadding(new Insets(4));

        Label l1 = new Label(name1); l1.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        Label l2 = new Label(name2); l2.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        Label l3 = new Label(name3); l3.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        grid.add(l1, 0, 0); grid.add(val1, 1, 0);
        grid.add(l2, 0, 1); grid.add(val2, 1, 1);
        grid.add(l3, 0, 2); grid.add(val3, 1, 2);

        return grid;
    }

    private TitledPane createSection(String title, GridPane content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(true);
        pane.setCollapsible(true);
        pane.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px;");
        return pane;
    }
}

