package simulation.view;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import simulation.model.HumanModel;
import simulation.ml.Keyframe;
import simulation.ml.JointTargets;
import simulation.ml.ReferenceTrajectory;
import simulation.vision.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

/** Optional capture workspace. Its independent animation timer also works while simulation is paused. */
public final class MotionCapturePanel extends VBox {
    private static final int[][] CONNECTIONS = {{11,12},{11,13},{13,15},{12,14},{14,16},{11,23},{12,24},
            {23,24},{23,25},{25,27},{27,29},{29,31},{27,31},{24,26},{26,28},{28,30},{30,32},{28,32}};
    private static final Color[] ANGLE_COLORS = {Color.web("#53e0cb"), Color.web("#b7a1ff"), Color.web("#ffca83")};
    private final PoseInputService camera = new PoseInputService();
    private final Supplier<HumanModel> human;
    private final Consumer<PoseFrame> onPose;
    private final Runnable onLost;
    private final Consumer<Boolean> onExpanded;
    private final ToggleButton cameraButton = new ToggleButton("Enable camera");
    private final ToggleButton poseButton = new ToggleButton("Enable MediaPipe");
    private final ToggleButton panelButton = new ToggleButton("Motion capture");
    private final Label status = label("Camera off", "capture-muted");
    private final Label sourceBadge = label("LOCAL / CAMERA OFF", "capture-eyebrow");
    private final Label fps = label("—", "capture-value"), latency = label("—", "capture-value"), confidence = label("—", "capture-value");
    private final Label quality = label("No pose data yet", "capture-muted");
    private final ProgressBar referenceMatch = new ProgressBar(0);
    private final Label referenceStatus = label("Choose a keyframe label to compare its reference pose.", "capture-muted");
    private final Label calibrationStatus = label("Calibration recommended", "capture-muted");
    private final Label sessionStatus = label("Sessions save landmarks and measurements only.", "capture-muted");
    private final Label recordingStatus = label("", "capture-muted");
    private final ImageView video = new ImageView();
    private final Canvas overlay = new Canvas(320, 180);
    private final Canvas chart = new Canvas(320, 90);
    private final StackPane preview = new StackPane();
    private final VBox placeholder = new VBox(8);
    private final Label previewTitle = label("Your movement, in view", "capture-title");
    private final Label previewHint = label("Enable the device camera to begin.\nPlace it to your side with your full body visible.", "capture-muted");
    private final Label[][] angles = new Label[3][3];
    private final Spinner<Integer> device = new Spinner<>(0, 20, 0);
    private final ComboBox<PoseMapper.Side> side = new ComboBox<>();
    private final ComboBox<PoseMapper.Facing> facing = new ComboBox<>();
    private final CheckBox mirror = new CheckBox("Mirror preview");
    private final Button calibrate = new Button("Calibrate standing pose");
    private final Button record = new Button("Record session");
    private final Button openReplay = new Button("Open replay…");
    private final Button replayAgain = new Button("Replay again");
    private final ComboBox<String> keyframe = new ComboBox<>();
    private final AtomicReference<String> keyframeLabel = new AtomicReference<>("Unlabelled");
    private final AtomicReference<PoseSessionRecorder> recorder = new AtomicReference<>();
    private final Deque<double[]> history = new ArrayDeque<>();
    private ReplayPoseSource replay;
    private PoseFrame displayed;
    private PoseInputService.Preview displayedPreview;
    private long lastTick;
    private boolean closed, finishingRecording;
    private final AnimationTimer timer;

    public MotionCapturePanel(Supplier<HumanModel> human, Consumer<PoseFrame> onPose,
                              Runnable onLost, Consumer<Boolean> onExpanded) {
        this.human = human; this.onPose = onPose; this.onLost = onLost; this.onExpanded = onExpanded;
        getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/motion-capture.css")).toExternalForm());
        getStyleClass().add("capture-panel");
        setSpacing(14); setPadding(new Insets(16)); setMinWidth(270); setPrefWidth(340);
        Label title = label("Motion capture", "capture-title");
        Label description = label("Camera → pose → NOVA", "capture-muted");
        getChildren().addAll(new VBox(4, sourceBadge, title, description), buildPreview(), buildMetrics(),
                buildAngleTable(), buildChart(), buildSettings(), buildRecorder());
        Label privacy = label("Processed on this device. Camera images are never saved. R&D observation only.", "capture-muted");
        privacy.setWrapText(true); getChildren().add(privacy);
        setControls();
        camera.onFrame(frame -> {
            PoseSessionRecorder active = recorder.get();
            if (active != null) active.record(frame, keyframeLabel.get());
        });
        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (now - lastTick < 50_000_000L) return;
                lastTick = now;
                refresh();
            }
        };
        timer.start();
    }

    public Node toolbar() {
        FlowPane buttons = new FlowPane(8, 6, panelButton, cameraButton, poseButton);
        buttons.getStyleClass().add("capture-toolbar");
        buttons.getStylesheets().add(getStylesheets().get(0));
        status.setWrapText(true); status.setMaxWidth(Double.MAX_VALUE);
        VBox bar = new VBox(6, buttons, status);
        bar.setPadding(new Insets(10, 12, 10, 12));
        bar.setStyle("-fx-background-color: #171b2a; -fx-border-color: transparent transparent #303747 transparent;");
        bar.getStylesheets().add(getStylesheets().get(0));
        return bar;
    }

    private Node buildPreview() {
        preview.getStyleClass().add("capture-preview");
        preview.setMinHeight(150); preview.setPrefHeight(185);
        video.setPreserveRatio(true); video.setSmooth(true);
        video.fitWidthProperty().bind(preview.widthProperty());
        video.fitHeightProperty().bind(preview.heightProperty());
        overlay.widthProperty().bind(preview.widthProperty());
        overlay.heightProperty().bind(preview.heightProperty());
        overlay.setMouseTransparent(true);
        previewTitle.setWrapText(true); previewHint.setWrapText(true);
        placeholder.setAlignment(Pos.CENTER); placeholder.setPadding(new Insets(18));
        previewTitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        previewHint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        placeholder.getChildren().addAll(previewTitle, previewHint);
        placeholder.setMouseTransparent(true);
        preview.getChildren().addAll(video, overlay, placeholder);
        preview.widthProperty().addListener((o, a, b) -> drawOverlay());
        preview.heightProperty().addListener((o, a, b) -> drawOverlay());
        return preview;
    }
    private Node buildMetrics() {
        HBox row = new HBox(8, metric("FPS", fps), metric("FRAME AGE", latency), metric("CONFIDENCE", confidence));
        for (Node item : row.getChildren()) HBox.setHgrow(item, Priority.ALWAYS);
        return new VBox(8, row, quality);
    }
    private VBox metric(String title, Label value) {
        VBox box = new VBox(5, label(title, "capture-eyebrow"), value);
        box.getStyleClass().add("capture-metric"); box.setMaxWidth(Double.MAX_VALUE); return box;
    }
    private Node buildAngleTable() {
        GridPane table = new GridPane(); table.setHgap(9); table.setVgap(8);
        String[] headers = {"JOINT", "RAW", "POSE", "SIM"};
        for (int i = 0; i < headers.length; i++) table.add(label(headers[i], "capture-eyebrow"), i, 0);
        String[] names = {"Hip", "Knee", "Ankle"};
        for (int i = 0; i < 3; i++) {
            Label joint = label(names[i], "capture-body"); joint.setTextFill(ANGLE_COLORS[i]); table.add(joint, 0, i + 1);
            for (int j = 0; j < 3; j++) { angles[i][j] = label("—", "capture-number"); table.add(angles[i][j], j + 1, i + 1); }
        }
        Label convention = label("Degrees · raw camera / calibrated pose / simulation", "capture-muted");
        convention.setWrapText(true);
        referenceMatch.setMaxWidth(Double.MAX_VALUE);
        referenceMatch.setStyle("-fx-accent: #53e0cb;");
        referenceStatus.setWrapText(true);
        return new VBox(9, label("Joint observations", "capture-heading"), table, convention, referenceMatch, referenceStatus);
    }
    private Node buildChart() {
        StackPane container = new StackPane(chart);
        container.setMinHeight(90); container.setPrefHeight(90);
        chart.widthProperty().bind(widthProperty().subtract(32));
        chart.widthProperty().addListener((o, a, b) -> drawChart());
        Label legend = label("Hip · Knee · Ankle     /     last 10 seconds", "capture-muted");
        legend.setWrapText(true);
        return new VBox(7, label("Live joint trajectories", "capture-heading"), container, legend);
    }
    private Node buildSettings() {
        side.getItems().setAll(PoseMapper.Side.values()); side.setValue(PoseMapper.Side.LEFT);
        facing.getItems().setAll(PoseMapper.Facing.values()); facing.setValue(PoseMapper.Facing.RIGHT);
        device.setMaxWidth(Double.MAX_VALUE); device.setPrefWidth(100);
        side.setMaxWidth(Double.MAX_VALUE); facing.setMaxWidth(Double.MAX_VALUE);
        GridPane settings = new GridPane(); settings.setHgap(12); settings.setVgap(8);
        settings.add(label("Device index", "capture-body"), 0, 0); settings.add(device, 1, 0);
        settings.add(label("Visible side", "capture-body"), 0, 1); settings.add(side, 1, 1);
        settings.add(label("Subject faces", "capture-body"), 0, 2); settings.add(facing, 1, 2);
        Label help = label("Start with camera 0. Stand side-on, keep shoulder to toe visible, then calibrate while still.", "capture-muted");
        help.setWrapText(true);
        calibrate.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(9, settings, mirror, calibrate, calibrationStatus, help);
        TitledPane pane = new TitledPane("Capture setup", box); pane.setExpanded(true);
        return pane;
    }
    private Node buildRecorder() {
        keyframe.getItems().setAll("Unlabelled", "Standing", "Descending", "Prostration", "Ascending", "Return standing");
        keyframe.setValue("Unlabelled"); keyframe.setMaxWidth(Double.MAX_VALUE);
        record.getStyleClass().add("capture-primary"); record.setMaxWidth(Double.MAX_VALUE);
        openReplay.setMaxWidth(Double.MAX_VALUE); replayAgain.setMaxWidth(Double.MAX_VALUE);
        FlowPane actions = new FlowPane(8, 8, openReplay, replayAgain);
        sessionStatus.setWrapText(true); recordingStatus.setWrapText(true);
        return new VBox(9, label("Dataset session", "capture-heading"), label("Keyframe label", "capture-muted"),
                keyframe, record, recordingStatus, actions, sessionStatus);
    }
    private void setControls() {
        panelButton.setOnAction(e -> onExpanded.accept(panelButton.isSelected()));
        cameraButton.setOnAction(e -> {
            if (cameraButton.isSelected()) startCamera(); else stopCamera();
        });
        poseButton.setOnAction(e -> {
            if (poseButton.isSelected()) {
                if (!cameraButton.isSelected()) { cameraButton.setSelected(true); startCamera(); }
                showPanel(); camera.enablePose(true);
            } else { stopRecording(); camera.enablePose(false); clearPose(); }
        });
        calibrate.setOnAction(e -> camera.mapper().calibrate());
        side.setOnAction(e -> configureMapping()); facing.setOnAction(e -> configureMapping());
        mirror.setOnAction(e -> { video.setScaleX(mirror.isSelected() ? -1 : 1); drawOverlay(); });
        keyframe.setOnAction(e -> keyframeLabel.set(keyframe.getValue()));
        record.setOnAction(e -> { if (recorder.get() != null) stopRecording(); else startRecording(); });
        openReplay.setOnAction(e -> chooseReplay());
        replayAgain.setOnAction(e -> { if (replay != null) { clearPose(); replay.start(); } });
        cameraButton.setTooltip(new Tooltip("Open the selected device camera; images stay on this computer."));
        poseButton.setTooltip(new Tooltip("Enable local pose estimation. Also opens the camera if needed."));
        configureMapping();
    }
    private void configureMapping() {
        camera.mapper().configure(side.getValue(), facing.getValue()); clearPose();
    }
    private void showPanel() { panelButton.setSelected(true); onExpanded.accept(true); }
    private void startCamera() {
        stopRecording();
        if (replay != null) { replay.stop(); replay = null; }
        clearPose(); showPanel(); camera.setCameraIndex(device.getValue()); camera.start();
    }
    private void stopCamera() {
        stopRecording(); camera.stop(); poseButton.setSelected(false); cameraButton.setSelected(false);
        video.setImage(null); displayedPreview = null; clearPose();
    }
    private void clearPose() {
        displayed = null; history.clear(); onLost.run(); drawOverlay(); drawChart();
    }

    private void refresh() {
        if (closed) return;
        PoseInputService.Status state = camera.status();
        if (replay == null && state.state() == PoseInputService.State.ERROR) {
            stopRecording(); cameraButton.setSelected(false); poseButton.setSelected(false);
            video.setImage(null); displayedPreview = null; onLost.run();
        }
        if (state.state() == PoseInputService.State.CAMERA && state.message().startsWith("MediaPipe unavailable")) {
            poseButton.setSelected(false); stopRecording();
        }
        boolean healthy = replay == null ? poseButton.isSelected() && camera.isHealthy() : replay.isHealthy();
        PoseFrame next = replay == null ? camera.latest().orElse(null) : replay.latest().orElse(null);
        if (replay == null) {
            PoseInputService.Preview snapshot = camera.preview().orElse(null);
            if (snapshot != null && snapshot != displayedPreview && cameraButton.isSelected()) {
                displayedPreview = snapshot;
                video.setImage(new Image(new ByteArrayInputStream(snapshot.jpeg())));
            }
            if (displayedPreview != null && cameraButton.isSelected()) latency.setText(format("%.0f ms",
                    displayedPreview.latencyMs() + (System.nanoTime() - displayedPreview.receivedNanos()) / 1e6));
            fps.setText(cameraButton.isSelected() ? format("%.0f", camera.metrics().fps()) : "—");
            if (!cameraButton.isSelected()) latency.setText("—");
            sourceBadge.setText(healthy ? "LOCAL / LIVE POSE" : cameraButton.isSelected() ? "LOCAL / CAMERA" : "LOCAL / CAMERA OFF");
            status.setText(state.message());
            quality.setText(poseButton.isSelected() ? format("%.1f%% tracking loss · %.0f ms inference", camera.metrics().invalidPercent(),
                    displayedPreview == null ? 0 : displayedPreview.inferenceMs()) : "Enable MediaPipe for joint observations");
        } else {
            video.setImage(null); fps.setText("REPLAY"); latency.setText("—");
            sourceBadge.setText("LOCAL / SESSION REPLAY"); status.setText(replay.status());
            quality.setText("Recorded label: " + replay.label());
        }
        if (next != null && next != displayed) {
            displayed = next;
            double[] values = next.trackingValid() ? new double[]{next.timestampMs() / 1000.0,
                    Math.toDegrees(next.hipAngleRad()), Math.toDegrees(next.kneeAngleRad()), Math.toDegrees(next.ankleAngleRad())}
                    : new double[]{next.timestampMs() / 1000.0, Double.NaN, Double.NaN, Double.NaN};
            history.addLast(values);
            while (history.size() > 300 || (!history.isEmpty() && values[0] - history.getFirst()[0] > 10)) history.removeFirst();
        }
        if (healthy && next != null) onPose.accept(next); else onLost.run();
        updateAngles(healthy ? next : null);
        updateReference(healthy ? next : null);
        confidence.setText(healthy ? format("%.0f%%", next.confidence() * 100) : "—");
        placeholder.setVisible(video.getImage() == null && (replay == null || displayed == null));
        if (replay != null) { previewTitle.setText("Session replay"); previewHint.setText("Replaying landmarks. Camera images were not retained."); }
        else { previewTitle.setText("Your movement, in view"); previewHint.setText("Enable the device camera to begin.\nKeep your full body visible from the side."); }
        calibrationStatus.setText(replay == null ? camera.mapper().calibrationStatus()
                : next != null && next.calibrated() ? "Recorded standing calibration" : "Recorded without calibration");
        PoseSessionRecorder active = recorder.get();
        boolean recording = active != null;
        record.setText(recording ? "Stop & save session" : finishingRecording ? "Saving session…" : "Record session");
        record.setDisable(finishingRecording || (!recording && (!healthy || replay != null)));
        if (active != null) {
            recordingStatus.setText("Recording · " + active.count() + " frames");
            if (active.failure() != null) stopRecording();
        }
        calibrate.setDisable(!healthy || replay != null || recording || finishingRecording);
        device.setDisable(cameraButton.isSelected());
        side.setDisable(replay != null || recording || finishingRecording);
        facing.setDisable(replay != null || recording || finishingRecording);
        keyframe.setDisable(replay != null);
        openReplay.setDisable(finishingRecording || recording);
        replayAgain.setDisable(replay == null || replay.isRunning());
        drawOverlay(); drawChart();
    }

    private void updateAngles(PoseFrame pose) {
        HumanModel model = human.get();
        double[] raw = pose == null ? null : new double[]{pose.rawHipAngleRad(), pose.rawKneeAngleRad(), pose.rawAnkleAngleRad()};
        double[] mapped = pose == null ? null : new double[]{pose.hipAngleRad(), pose.kneeAngleRad(), pose.ankleAngleRad()};
        double[] sim = {model.getHipJoint().getAngle(), model.getKneeJoint().getAngle(), model.getAnkleJoint().getAngle()};
        for (int i = 0; i < 3; i++) {
            angles[i][0].setText(raw == null ? "—" : degrees(raw[i]));
            angles[i][1].setText(mapped == null ? "—" : degrees(mapped[i]));
            angles[i][2].setText(degrees(sim[i]));
        }
    }
    private void updateReference(PoseFrame pose) {
        String targetLabel = replay == null ? keyframe.getValue() : replay.label();
        Keyframe target = switch (targetLabel) {
            case "Standing" -> Keyframe.STANDING;
            case "Descending" -> Keyframe.DESCENDING;
            case "Prostration" -> Keyframe.PROSTRATION;
            case "Ascending" -> Keyframe.ASCENDING;
            case "Return standing" -> Keyframe.RETURN_STANDING;
            default -> null;
        };
        if (pose == null || target == null) {
            referenceMatch.setProgress(0);
            referenceStatus.setText("Select a label to compare the AI reference pose."); return;
        }
        JointTargets reference = new ReferenceTrajectory().poseFor(target);
        double error = Math.sqrt((Math.pow(pose.hipAngleRad() - reference.hipAngle(), 2)
                + Math.pow(pose.kneeAngleRad() - reference.kneeAngle(), 2)
                + Math.pow(pose.ankleAngleRad() - reference.ankleAngle(), 2)) / 3);
        referenceMatch.setProgress(Math.max(0, 1 - Math.toDegrees(error) / 45));
        referenceStatus.setText(format("%s reference · %.1f° RMS difference", targetLabel, Math.toDegrees(error)));
    }
    private void drawOverlay() {
        GraphicsContext g = overlay.getGraphicsContext2D(); double w = overlay.getWidth(), h = overlay.getHeight();
        g.clearRect(0, 0, w, h);
        if (displayed == null || displayed.landmarks().size() != 33) return;
        if (replay == null && (!cameraButton.isSelected() || !poseButton.isSelected()
                || displayedPreview == null || System.nanoTime() - displayedPreview.receivedNanos() > 500_000_000L)) return;
        double scale = Math.min(w / displayed.width(), h / displayed.height());
        double imageW = displayed.width() * scale, imageH = displayed.height() * scale;
        double left = (w - imageW) / 2, top = (h - imageH) / 2;
        g.save(); g.beginPath(); g.rect(left, top, imageW, imageH); g.clip();
        g.setLineWidth(2.2); g.setStroke(Color.web("#53e0cb")); g.setFill(Color.web("#d5fff8"));
        List<PoseFrame.Landmark> points = displayed.landmarks();
        for (int[] connection : CONNECTIONS) {
            PoseFrame.Landmark a = points.get(connection[0]), b = points.get(connection[1]);
            if (Math.min(a.confidence(), b.confidence()) >= PoseFrame.MIN_CONFIDENCE)
                g.strokeLine(left + px(a.x()) * imageW, top + a.y() * imageH, left + px(b.x()) * imageW, top + b.y() * imageH);
        }
        for (PoseFrame.Landmark p : points) if (p.confidence() >= PoseFrame.MIN_CONFIDENCE)
            g.fillOval(left + px(p.x()) * imageW - 2.7, top + p.y() * imageH - 2.7, 5.4, 5.4);
        g.restore();
    }
    private double px(double x) { return mirror.isSelected() ? 1 - x : x; }
    private void drawChart() {
        GraphicsContext g = chart.getGraphicsContext2D(); double w = chart.getWidth(), h = chart.getHeight();
        g.setFill(Color.web("#121726")); g.fillRect(0, 0, w, h);
        g.setStroke(Color.web("#293244")); g.setLineWidth(1);
        for (int i = 1; i < 4; i++) g.strokeLine(0, h * i / 4, w, h * i / 4);
        if (history.isEmpty()) return;
        double end = history.getLast()[0];
        for (int joint = 1; joint <= 3; joint++) {
            g.setStroke(ANGLE_COLORS[joint - 1]); g.setLineWidth(1.6);
            double[] previous = null;
            for (double[] sample : history) {
                if (previous != null && Double.isFinite(previous[joint]) && Double.isFinite(sample[joint]) && sample[0] - previous[0] < 0.5)
                    g.strokeLine(w * (1 - (end - previous[0]) / 10), h * (1 - (previous[joint] + 50) / 200),
                            w * (1 - (end - sample[0]) / 10), h * (1 - (sample[joint] + 50) / 200));
                previous = sample;
            }
        }
    }
    private void startRecording() {
        FileChooser chooser = sessionChooser("Save pose dataset"); chooser.setInitialFileName("nova-pose-" + System.currentTimeMillis() + ".jsonl");
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        if (!camera.isHealthy()) { sessionStatus.setText("Tracking was lost. Restore tracking before starting a session."); return; }
        recorder.set(new PoseSessionRecorder(file.toPath())); sessionStatus.setText("Recording locally · no camera images saved");
    }
    private void stopRecording() {
        PoseSessionRecorder active = recorder.getAndSet(null);
        if (active == null) return;
        finishingRecording = true;
        active.stop().whenComplete((path, error) -> Platform.runLater(() -> {
            finishingRecording = false;
            sessionStatus.setText(error == null ? "Saved " + active.count() + " frames · " + path.getFileName()
                    : "Save failed: " + error.getMessage());
            recordingStatus.setText("");
        }));
    }
    private void chooseReplay() {
        File file = sessionChooser("Open pose session").showOpenDialog(getScene().getWindow());
        if (file == null) return;
        stopCamera(); if (replay != null) replay.stop();
        replay = new ReplayPoseSource(file.toPath()); replay.start(); showPanel();
        sessionStatus.setText("Replaying " + file.getName());
    }
    private FileChooser sessionChooser(String title) {
        FileChooser chooser = new FileChooser(); chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NOVA pose session", "*.jsonl")); return chooser;
    }
    public void shutdown() {
        closed = true; timer.stop(); stopRecording(); camera.close(); if (replay != null) replay.close();
        video.setImage(null); onLost.run();
    }
    private static Label label(String text, String style) { Label label = new Label(text); label.getStyleClass().add(style); return label; }
    private static String degrees(double radians) { return format("%.1f°", Math.toDegrees(radians)); }
    private static String format(String template, Object... values) { return String.format(Locale.ROOT, template, values); }
}
