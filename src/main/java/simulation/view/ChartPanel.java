package simulation.view;

import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import simulation.model.SimulationState;

/**
 * UI panel with real-time charts for joint angles and motor torques.
 * Uses JavaFX {@link LineChart} with toggle checkboxes for each signal.
 */
public class ChartPanel extends VBox {

    private static final int MAX_DATA_POINTS = 500;

    // --- Joint angle chart ---
    private final XYChart.Series<Number, Number> hipAngleSeries   = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> kneeAngleSeries  = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> ankleAngleSeries = new XYChart.Series<>();

    // --- Motor torque chart ---
    private final XYChart.Series<Number, Number> hipTorqueSeries   = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> kneeTorqueSeries  = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> ankleTorqueSeries = new XYChart.Series<>();

    private final LineChart<Number, Number> angleChart;
    private final LineChart<Number, Number> torqueChart;

    public ChartPanel() {
        setSpacing(8);
        setPadding(new Insets(5));
        setStyle("-fx-background-color: #2b2b2b;");

        // --- Joint Angle Chart ---
        hipAngleSeries.setName("Hip");
        kneeAngleSeries.setName("Knee");
        ankleAngleSeries.setName("Ankle");

        NumberAxis angleTimeAxis = new NumberAxis();
        angleTimeAxis.setLabel("Time (s)");
        angleTimeAxis.setAutoRanging(true);
        angleTimeAxis.setForceZeroInRange(false);

        NumberAxis angleAxis = new NumberAxis();
        angleAxis.setLabel("Angle (°)");
        angleAxis.setAutoRanging(true);

        angleChart = new LineChart<>(angleTimeAxis, angleAxis);
        angleChart.setTitle("Joint Angles");
        angleChart.setPrefHeight(200);
        angleChart.setCreateSymbols(false);
        angleChart.setAnimated(false);
        angleChart.getData().addAll(hipAngleSeries, kneeAngleSeries, ankleAngleSeries);
        angleChart.setStyle("-fx-background-color: #1e1e1e;");

        // --- Motor Torque Chart ---
        hipTorqueSeries.setName("Hip");
        kneeTorqueSeries.setName("Knee");
        ankleTorqueSeries.setName("Ankle");

        NumberAxis torqueTimeAxis = new NumberAxis();
        torqueTimeAxis.setLabel("Time (s)");
        torqueTimeAxis.setAutoRanging(true);
        torqueTimeAxis.setForceZeroInRange(false);

        NumberAxis torqueAxis = new NumberAxis();
        torqueAxis.setLabel("Torque (N·m)");
        torqueAxis.setAutoRanging(true);

        torqueChart = new LineChart<>(torqueTimeAxis, torqueAxis);
        torqueChart.setTitle("Motor Torques");
        torqueChart.setPrefHeight(200);
        torqueChart.setCreateSymbols(false);
        torqueChart.setAnimated(false);
        torqueChart.getData().addAll(hipTorqueSeries, kneeTorqueSeries, ankleTorqueSeries);
        torqueChart.setStyle("-fx-background-color: #1e1e1e;");

        // --- Toggle checkboxes ---
        CheckBox showAngles = new CheckBox("Show Angles");
        showAngles.setSelected(true);
        showAngles.setStyle("-fx-text-fill: #e0e0e0;");
        showAngles.selectedProperty().addListener((obs, o, n) -> angleChart.setVisible(n));

        CheckBox showTorques = new CheckBox("Show Torques");
        showTorques.setSelected(true);
        showTorques.setStyle("-fx-text-fill: #e0e0e0;");
        showTorques.selectedProperty().addListener((obs, o, n) -> torqueChart.setVisible(n));

        getChildren().addAll(showAngles, angleChart, showTorques, torqueChart);
    }

    /**
     * Adds a data point to each chart series from the current simulation state.
     * Must be called on the JavaFX Application Thread.
     */
    public void update(SimulationState state) {
        double time = state.getTime();

        // Joint angles
        addPoint(hipAngleSeries,   time, Math.toDegrees(state.getHumanModel().getThigh().getAngle()));
        addPoint(kneeAngleSeries,  time, Math.toDegrees(state.getHumanModel().getKneeJoint().getAngle()));
        addPoint(ankleAngleSeries, time, Math.toDegrees(state.getHumanModel().getAnkleJoint().getAngle()));

        // Motor torques
        addPoint(hipTorqueSeries,   time, state.getExoskeletonModel().getHipMotor().getOutputTorque());
        addPoint(kneeTorqueSeries,  time, state.getExoskeletonModel().getKneeMotor().getOutputTorque());
        addPoint(ankleTorqueSeries, time, state.getExoskeletonModel().getAnkleMotor().getOutputTorque());
    }

    /** Clears all chart data. */
    public void clear() {
        hipAngleSeries.getData().clear();
        kneeAngleSeries.getData().clear();
        ankleAngleSeries.getData().clear();
        hipTorqueSeries.getData().clear();
        kneeTorqueSeries.getData().clear();
        ankleTorqueSeries.getData().clear();
    }

    private void addPoint(XYChart.Series<Number, Number> series, double x, double y) {
        series.getData().add(new XYChart.Data<>(x, y));
        if (series.getData().size() > MAX_DATA_POINTS) {
            series.getData().remove(0);
        }
    }
}
