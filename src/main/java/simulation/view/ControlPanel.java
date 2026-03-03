package simulation.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import simulation.physics.SimulationEngine;

/**
 * UI panel with simulation controls: Play, Pause, Step, Reset, speed slider,
 * and basic parameter inputs.
 */
public class ControlPanel extends VBox {

    private final SimulationEngine engine;
    private final Label timeLabel;
    private final Label statusLabel;
    private final Button playBtn;
    private final Button pauseBtn;
    private final Button sideViewBtn;
    private final Button frontViewBtn;
    private final Slider speedSlider;

    // Callbacks for parameter changes (rebuild model)
    private Runnable onParameterChange;

    public ControlPanel(SimulationEngine engine) {
        this.engine = engine;
        setSpacing(10);
        setPadding(new Insets(10));
        setPrefWidth(280);
        setStyle("-fx-background-color: #2b2b2b;");

        // --- Title ---
        Label title = new Label("Simulation Controls");
        title.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 16px; -fx-font-weight: bold;");

        // --- Status ---
        statusLabel = new Label("Status: Stopped");
        statusLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        timeLabel = new Label("Time: 0.000 s");
        timeLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 14px;");

        // --- Transport controls ---
        playBtn = createButton("▶ Play", "#4CAF50");
        pauseBtn = createButton("⏸ Pause", "#FF9800");
        Button stepBtn = createButton("⏭ Step", "#2196F3");
        Button resetBtn = createButton("⏹ Reset", "#f44336");

        playBtn.setOnAction(e -> {
            engine.play();
            updateStatus();
        });
        pauseBtn.setOnAction(e -> {
            engine.pause();
            updateStatus();
        });
        stepBtn.setOnAction(e -> {
            engine.singleStep();
            updateStatus();
        });
        resetBtn.setOnAction(e -> {
            engine.reset();
            updateStatus();
        });

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

        // --- Camera controls ---
        Label cameraTitle = new Label("Camera");
        cameraTitle.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
        sideViewBtn = createButton("Side View", "#607D8B");
        frontViewBtn = createButton("Front View", "#607D8B");

        HBox cameraRow = new HBox(8, sideViewBtn, frontViewBtn);
        cameraRow.setAlignment(Pos.CENTER);

        // Store references for external wiring
        sideViewBtn.setId("sideViewBtn");
        frontViewBtn.setId("frontViewBtn");

        // --- Separator ---
        Separator sep1 = new Separator();
        Separator sep2 = new Separator();

        getChildren().addAll(
                title,
                statusLabel, timeLabel,
                sep1,
                transportRow1, transportRow2,
                speedLabel, speedSlider,
                sep2,
                cameraTitle, cameraRow
        );
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

    public Button getSideViewButton() {
        return sideViewBtn;
    }

    public Button getFrontViewButton() {
        return frontViewBtn;
    }

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




