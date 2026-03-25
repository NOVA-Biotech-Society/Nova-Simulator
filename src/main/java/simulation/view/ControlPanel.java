package simulation.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import simulation.physics.SimulationEngine;

/**
 * UI panel with simulation controls: Play, Pause, Step, Reset, speed slider,
 * human parameter inputs, exoskeleton parameter inputs, and camera controls.
 */
public class ControlPanel extends VBox {

    private final SimulationEngine engine;
    private final Label timeLabel;
    private final Label statusLabel;
    private final Button playBtn;
    private final Button pauseBtn;
    private final Button sideViewBtn;
    private final Button frontViewBtn;
    private final Button importBtn;
    private final Button exportBtn;
    private final Slider speedSlider;
    private Slider heightSlider, massSlider, torqueSlider, powerSlider;
    private Label heightSliderValue, massSliderValue, torqueSliderValue, powerSliderValue;
    // Callbacks for parameter changes (rebuild model)
    private Runnable onParameterChange;

    public ControlPanel(SimulationEngine engine) {
        this.engine = engine;
        setSpacing(8);
        setPadding(new Insets(10));
        setPrefWidth(280);
        setStyle("-fx-background-color: #2b2b2b;");

        // --- Title ---
        Label title = new Label("Nova Exoskeleton Simulator");
        title.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 15px; -fx-font-weight: bold;");

        // --- Status ---
        statusLabel = new Label("Status: Stopped");
        statusLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        timeLabel = new Label("Time: 0.000 s");
        timeLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 14px;");

        Label scenarioLabel = new Label("Scenario: Prostration (Sujood)");
        scenarioLabel.setStyle("-fx-text-fill: #90CAF9; -fx-font-size: 11px;");

        // --- Transport controls ---
        playBtn = createButton("▶ Play", "#4CAF50");
        pauseBtn = createButton("⏸ Pause", "#FF9800");
        Button stepBtn = createButton("⏭ Step", "#2196F3");
        Button resetBtn = createButton("⏹ Reset", "#f44336");

        playBtn.setOnAction(e -> { engine.play(); updateStatus(); });
        pauseBtn.setOnAction(e -> { engine.pause(); updateStatus(); });
        stepBtn.setOnAction(e -> { engine.singleStep(); updateStatus(); });
        resetBtn.setOnAction(e -> { engine.reset(); updateStatus(); });

        HBox transportRow1 = new HBox(8, playBtn, pauseBtn);
        HBox transportRow2 = new HBox(8, stepBtn, resetBtn);
        transportRow1.setAlignment(Pos.CENTER);
        transportRow2.setAlignment(Pos.CENTER);

        // --- Speed slider ---
        Label speedLabel = new Label("Speed: 1.0x");
        speedLabel.setStyle("-fx-text-fill: #e0e0e0;");
        speedSlider = new Slider(0.1, 5.0, 1.0);
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(1.0);
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double speed = newVal.doubleValue();
            engine.setSpeedMultiplier(speed);
            speedLabel.setText(String.format("Speed: %.1fx", speed));
        });

        // --- Human parameters ---
        TitledPane humanPane = createHumanParameterPane();

        // --- Exo parameters ---
        TitledPane exoPane = createExoParameterPane();

        // --- Camera controls ---
        Label cameraTitle = new Label("Camera");
        cameraTitle.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
        sideViewBtn = createButton("Side View", "#607D8B");
        frontViewBtn = createButton("Front View", "#607D8B");
        sideViewBtn.setId("sideViewBtn");
        frontViewBtn.setId("frontViewBtn");
        HBox cameraRow = new HBox(8, sideViewBtn, frontViewBtn);
        cameraRow.setAlignment(Pos.CENTER);

        // --- Import Export Section ---
        Label parametersTitle = new Label("Parameters");
        parametersTitle.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
        importBtn = createButton("Import", "#10bb00");
        exportBtn = createButton("Export", "#ffa436");
        importBtn.setId("importBtn");
        exportBtn.setId("exportBtn");
        HBox importExportRow = new HBox(8, importBtn, exportBtn);
        importExportRow.setAlignment(Pos.CENTER);



        Separator sep1 = new Separator();
        Separator sep2 = new Separator();

        getChildren().addAll(
                title, scenarioLabel,
                statusLabel, timeLabel,
                sep1,
                transportRow1, transportRow2,
                speedLabel, speedSlider,
                sep2,
                parametersTitle,
                humanPane, exoPane,
                importExportRow,
                cameraTitle, cameraRow
        );
    }

    private TitledPane createHumanParameterPane() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(4));

        var heightModel = engine.getState().getHumanModel();

        // Height slider
        Label hLabel = new Label("Height:");
        hLabel.setStyle("-fx-text-fill: #ccc;");
        heightSliderValue = new Label(String.format("%.2f m", heightModel.getHeight()));
        heightSliderValue.setStyle("-fx-text-fill: #00e676; -fx-font-family: Consolas;");
        heightSlider = new Slider(1.40, 2.10, heightModel.getHeight());
        heightSlider.setShowTickLabels(true);
        heightSlider.setMajorTickUnit(0.1);
        heightSlider.valueProperty().addListener((obs, o, n) -> {
            heightSliderValue.setText(String.format("%.2f m", n.doubleValue()));

            //Here we update the human height (it affects all the segmentations in the simulation)
            engine.getState().getHumanModel().setHeight(n.doubleValue());
            if (onParameterChange != null) {
                onParameterChange.run();
            }
        });

        // Mass slider
        Label mLabel = new Label("Mass:");
        mLabel.setStyle("-fx-text-fill: #ccc;");
        massSliderValue = new Label(String.format("%.0f kg", heightModel.getTotalMass()));
        massSliderValue.setStyle("-fx-text-fill: #00e676; -fx-font-family: Consolas;");
        massSlider = new Slider(40, 150, heightModel.getTotalMass());
        massSlider.setShowTickLabels(true);
        massSlider.setMajorTickUnit(20);
        massSlider.valueProperty().addListener((obs, o, n) -> {
            massSliderValue.setText(String.format("%.0f kg", n.doubleValue()));
            //Here we update the human mass (it affects all the segmentations in the simulation)
            engine.getState().getHumanModel().setTotalMass(n.doubleValue());
            if (onParameterChange != null) {
                onParameterChange.run();
            }
        });


        grid.add(hLabel, 0, 0); grid.add(heightSlider, 1, 0); grid.add(heightSliderValue, 2, 0);
        grid.add(mLabel, 0, 1); grid.add(massSlider, 1, 1); grid.add(massSliderValue, 2, 1);

        TitledPane pane = new TitledPane("Human Parameters", grid);
        pane.setExpanded(false);
        pane.setStyle("-fx-text-fill: #000000;");
        return pane;
    }

    private TitledPane createExoParameterPane() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(4));

        var exo = engine.getState().getExoskeletonModel();

        // Motor max torque
        Label tLabel = new Label("Motor τ:");
        tLabel.setStyle("-fx-text-fill: #ccc;");
        torqueSliderValue = new Label(String.format("%.0f N·m", exo.getHipMotor().getMaxTorque()));
        torqueSliderValue.setStyle("-fx-text-fill: #00e676; -fx-font-family: Consolas;");
        torqueSlider = new Slider(10, 200, exo.getHipMotor().getMaxTorque());
        torqueSlider.setShowTickLabels(true);
        torqueSlider.setMajorTickUnit(50);
        torqueSlider.valueProperty().addListener((obs, o, n) -> {
            double v = n.doubleValue();
            torqueSliderValue.setText(String.format("%.0f N·m", v));
            exo.setMotorMaxTorque(v);
        });

        // Motor max power
        Label pLabel = new Label("Motor P:");
        pLabel.setStyle("-fx-text-fill: #ccc;");
        powerSliderValue = new Label(String.format("%.0f W", exo.getHipMotor().getMaxPower()));
        powerSliderValue.setStyle("-fx-text-fill: #00e676; -fx-font-family: Consolas;");
        powerSlider = new Slider(50, 500, exo.getHipMotor().getMaxPower());
        powerSlider.setShowTickLabels(true);
        powerSlider.setMajorTickUnit(100);
        powerSlider.valueProperty().addListener((obs, o, n) -> {
            double v = n.doubleValue();
            powerSliderValue.setText(String.format("%.0f W", v));
            exo.setMotorMaxPower(v);
        });

        grid.add(tLabel, 0, 0); grid.add(torqueSlider, 1, 0); grid.add(torqueSliderValue, 2, 0);
        grid.add(pLabel, 0, 1); grid.add(powerSlider, 1, 1); grid.add(powerSliderValue, 2, 1);

        TitledPane pane = new TitledPane("Exoskeleton Parameters", grid);
        pane.setExpanded(false);
        pane.setStyle("-fx-text-fill: #000000;");
        return pane;
    }

    /** Updates the time display. Called from the UI thread. */
    public void updateTime(double time) {
        timeLabel.setText(String.format("Time: %.3f s", time));
        updateStatus();
    }

    private void updateStatus() {
        if (engine.isRunning()) {
            statusLabel.setText("Status: Running");
            statusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 12px;");
        } else {
            statusLabel.setText("Status: Paused");
            statusLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-size: 12px;");
        }
    }

    public void updateSliders() {
        var human = engine.getState().getHumanModel();
        var exo = engine.getState().getExoskeletonModel();

        // Updating sliders
        heightSlider.setValue(human.getHeight());
        massSlider.setValue(human.getTotalMass());
        torqueSlider.setValue(exo.getMotorMaxTorque());
        powerSlider.setValue(exo.getMotorMaxPower());

        // Updating labels
        heightSliderValue.setText(String.format("%.2f m", human.getHeight()));
        massSliderValue.setText(String.format("%.0f kg", human.getTotalMass()));
        torqueSliderValue.setText(String.format("%.0f N·m", exo.getHipMotor().getMaxTorque()));
        powerSliderValue.setText(String.format("%.0f W", exo.getHipMotor().getMaxPower()));
    }

    public Button getSideViewButton() { return sideViewBtn; }
    public Button getFrontViewButton() { return frontViewBtn; }
    public Button getImportBtn() { return importBtn; }
    public Button getExportBtn() { return exportBtn; }

    public void setOnParameterChange(Runnable callback) {
        this.onParameterChange = callback;
    }

    private Button createButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 12px; " +
                "-fx-padding: 6 14 6 14; -fx-background-radius: 4;", color));
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }
}




