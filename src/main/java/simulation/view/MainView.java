package simulation.view;

import javafx.geometry.Orientation;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import simulation.model.*;
import simulation.physics.SimulationEngine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Main view composing the 3D viewport, control panel, data panel, and chart panel.
 * <p>
 * Supports drag-and-drop for exoskeleton modules (motors and sensors) in the 3D viewport.
 * Click an exo module to select it, then drag to reposition along a segment.
 * </p>
 */
public class MainView extends BorderPane {

    private static final double SCALE = 1.0;

    // 3D shapes for human segments
    private Cylinder thighCyl, shankCyl, footCyl;

    // 3D shapes for exoskeleton segments
    private Cylinder exoThighCyl, exoShankCyl, exoFootCyl;

    // 3D shapes for joints
    private Sphere hipSphere, kneeSphere, ankleSphere;

    // Exoskeleton module nodes (draggable)
    private final List<DraggableModule> draggableModules = new ArrayList<>();

    // Materials
    private final PhongMaterial humanMaterial = new PhongMaterial(Color.RED);
    private final PhongMaterial exoMaterial   = new PhongMaterial(Color.color(0.5, 0.5, 0.5, 0.8));
    private final PhongMaterial jointMaterial = new PhongMaterial(Color.LIGHTGRAY);
    private final PhongMaterial dangerMaterial = new PhongMaterial(Color.RED);
    private final PhongMaterial motorMaterial = new PhongMaterial(Color.ORANGE);
    private final PhongMaterial motorSelectedMaterial = new PhongMaterial(Color.YELLOW);
    private final PhongMaterial sensorMaterial = new PhongMaterial(Color.LIMEGREEN);
    private final PhongMaterial sensorSelectedMaterial = new PhongMaterial(Color.GREENYELLOW);

    // Sub-panels
    private final ControlPanel controlPanel;
    private final DataPanel dataPanel;
    private final ChartPanel chartPanel;
    private final CameraController cameraController;

    // 3D scene root
    private Group root3D;
    private SubScene subScene;

    private final SimulationEngine engine;

    // Materials for hover state (slightly brighter versions)
    private final PhongMaterial motorHoverMaterial = new PhongMaterial(Color.LIGHTSALMON);
    private final PhongMaterial sensorHoverMaterial = new PhongMaterial(Color.LIGHTGREEN);

    // Currently selected draggable module
    private DraggableModule selectedModule = null;
    // Label in the bottom of the viewport showing module info
    private Label moduleInfoLabel;

    /**
     * Creates the main view layout.
     */
    public MainView(SimulationEngine engine) {
        this.engine = engine;

        // --- Build 3D scene ---
        root3D = build3DScene(engine.getState());

        subScene = new SubScene(root3D, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#1a1a2e"));

        // Camera
        cameraController = new CameraController(subScene);
        root3D.getChildren().add(cameraController.getCameraRig());

        // Lighting
        AmbientLight ambient = new AmbientLight(Color.web("#555555"));
        PointLight pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateX(2);
        pointLight.setTranslateY(-3);
        pointLight.setTranslateZ(-2);
        PointLight fillLight = new PointLight(Color.web("#404060"));
        fillLight.setTranslateX(-2);
        fillLight.setTranslateY(-1);
        fillLight.setTranslateZ(2);
        root3D.getChildren().addAll(ambient, pointLight, fillLight);

        // Module info overlay label
        moduleInfoLabel = new Label("");
        moduleInfoLabel.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 13px; -fx-font-weight: bold; " +
                "-fx-background-color: rgba(0,0,0,0.6); -fx-padding: 4 10 4 10; -fx-background-radius: 4;");
        moduleInfoLabel.setVisible(false);

        // LOGO Overlay
        Image logoImage = new Image(getClass().getResourceAsStream("/assets/nova_biotech_society_logo.jpg"));
        ImageView overlayImageView = new ImageView(logoImage);
        overlayImageView.setFitWidth(120);
        overlayImageView.setPreserveRatio(true);
        overlayImageView.setOpacity(0.5);

        // Wrap SubScene in a StackPane for overlay
        javafx.scene.layout.StackPane viewportStack = new javafx.scene.layout.StackPane(subScene, overlayImageView, moduleInfoLabel);
        javafx.scene.layout.StackPane.setAlignment(moduleInfoLabel, javafx.geometry.Pos.BOTTOM_LEFT);
        javafx.scene.layout.StackPane.setMargin(moduleInfoLabel, new javafx.geometry.Insets(0, 0, 10, 10));

        javafx.scene.layout.StackPane.setAlignment(overlayImageView, javafx.geometry.Pos.TOP_LEFT);
        javafx.scene.layout.StackPane.setMargin(overlayImageView, new javafx.geometry.Insets(10, 0, 0, 10));

        subScene.widthProperty().bind(viewportStack.widthProperty());
        subScene.heightProperty().bind(viewportStack.heightProperty());

        // --- Build side panels ---
        controlPanel = new ControlPanel(engine);
        dataPanel = new DataPanel();
        chartPanel = new ChartPanel();

        controlPanel.getSideViewButton().setOnAction(e -> cameraController.resetToSideView());
        controlPanel.getFrontViewButton().setOnAction(e -> cameraController.resetToFrontView());

        controlPanel.getImportBtn().setOnAction(e -> handleImport());
        controlPanel.getExportBtn().setOnAction(e -> handleExport());

        controlPanel.setOnParameterChange(() -> {
            //forcing the update
            updateTransforms(engine.getState());
        });

        // Right panel: tabbed
        TabPane tabPane = new TabPane();
        Tab controlTab = new Tab("Controls", controlPanel);
        controlTab.setClosable(false);

        ScrollPane dataScroll = new ScrollPane(dataPanel);
        dataScroll.setFitToWidth(true);
        dataScroll.setStyle("-fx-background-color: #2b2b2b;");
        Tab dataTab = new Tab("Data", dataScroll);
        dataTab.setClosable(false);

        ScrollPane chartScroll = new ScrollPane(chartPanel);
        chartScroll.setFitToWidth(true);
        chartScroll.setStyle("-fx-background-color: #2b2b2b;");
        Tab chartTab = new Tab("Charts", chartScroll);
        chartTab.setClosable(false);

        tabPane.getTabs().addAll(controlTab, dataTab, chartTab);
        tabPane.setPrefWidth(320);

        // Layout
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(viewportStack, tabPane);
        splitPane.setDividerPositions(0.7);

        setCenter(splitPane);

        // --- Wire simulation callback ---
        engine.setOnStepCallback(this::onSimulationStep);
    }

    // ====================================================================
    // 3D Scene Building
    // ====================================================================

    private Group build3DScene(SimulationState state) {
        Group root = new Group();

        HumanModel human = state.getHumanModel();
        double thighLen = human.getThigh().getLength();
        double shankLen = human.getShank().getLength();
        double footLen  = human.getFoot().getLength();
        double segWidth = human.getThigh().getWidth();

        // Human segments
        thighCyl = createCylinder(segWidth / 2, thighLen, humanMaterial);
        shankCyl = createCylinder(segWidth * 0.85 / 2, shankLen, humanMaterial);
        footCyl  = createCylinder(segWidth * 0.7 / 2, footLen, humanMaterial);

        // Exoskeleton segments (slightly wider shell)
        exoThighCyl = createCylinder(segWidth / 2 + 0.012, thighLen * 0.92, exoMaterial);
        exoShankCyl = createCylinder(segWidth * 0.85 / 2 + 0.012, shankLen * 0.92, exoMaterial);
        exoFootCyl  = createCylinder(segWidth * 0.7 / 2 + 0.012, footLen * 0.92, exoMaterial);

        // Joint spheres
        double jr = segWidth * 0.55;
        hipSphere   = new Sphere(jr);       hipSphere.setMaterial(jointMaterial);
        kneeSphere  = new Sphere(jr * 0.9); kneeSphere.setMaterial(jointMaterial);
        ankleSphere = new Sphere(jr * 0.8); ankleSphere.setMaterial(jointMaterial);

        root.getChildren().addAll(
                thighCyl, shankCyl, footCyl,
                exoThighCyl, exoShankCyl, exoFootCyl,
                hipSphere, kneeSphere, ankleSphere,
                createFloorGrid()
        );

        // --- Create draggable exo modules ---
        createDraggableModules(root, state);

        // Initial transform update
        updateTransforms(state);

        return root;
    }

    /** Creates a cylinder with given radius and height. */
    private Cylinder createCylinder(double radius, double height, PhongMaterial material) {
        Cylinder cyl = new Cylinder(radius * SCALE, height * SCALE);
        cyl.setMaterial(material);
        return cyl;
    }

    /**
     * Creates draggable module representations for each motor and sensor.
     */
    private void createDraggableModules(Group root, SimulationState state) {
        ExoskeletonModel exo = state.getExoskeletonModel();
        HumanModel human = state.getHumanModel();

        // Motors — represented as small boxes on the joint
        addDraggableMotor(root, "Hip Motor", exo.getHipMotor(), human.getThigh(), 0.0);
        addDraggableMotor(root, "Knee Motor", exo.getKneeMotor(), human.getShank(), 0.0);
        addDraggableMotor(root, "Ankle Motor", exo.getAnkleMotor(), human.getFoot(), 0.0);

        // Sensors — represented as small spheres on segments
        List<ImuSensor> sensors = exo.getSensors();
        if (sensors.size() >= 3) {
            addDraggableSensor(root, "Thigh IMU", sensors.get(0), human.getThigh());
            addDraggableSensor(root, "Shank IMU", sensors.get(1), human.getShank());
            addDraggableSensor(root, "Foot IMU",  sensors.get(2), human.getFoot());
        }
    }

    private void addDraggableMotor(Group root, String label, Motor motor,
                                   RigidBodySegment segment, double offsetFraction) {
        Box box = new Box(0.04, 0.03, 0.04);
        box.setMaterial(motorMaterial);

        DraggableModule mod = new DraggableModule(label, box, segment, offsetFraction,
                DraggableModule.ModuleType.MOTOR, motor, null);
        setupDragHandlers(mod);
        root.getChildren().add(box);
        draggableModules.add(mod);
    }

    private void addDraggableSensor(Group root, String label, ImuSensor sensor,
                                    RigidBodySegment segment) {
        Sphere sphere = new Sphere(0.018);
        sphere.setMaterial(sensorMaterial);

        double offset = sensor.getLocalOffsetFraction();
        DraggableModule mod = new DraggableModule(label, sphere, segment, offset,
                DraggableModule.ModuleType.SENSOR, null, sensor);
        setupDragHandlers(mod);
        root.getChildren().add(sphere);
        draggableModules.add(mod);
    }

    // ====================================================================
    // Drag & Drop interaction
    // ====================================================================

    private double dragStartY;
    private double dragStartOffset;

    private void setupDragHandlers(DraggableModule mod) {
        Node node = mod.node;

        node.setOnMousePressed((MouseEvent e) -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                e.consume();
                selectModule(mod);
                dragStartY = e.getSceneY();
                dragStartOffset = mod.offsetFraction;
            }
        });

        node.setOnMouseDragged((MouseEvent e) -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                e.consume();
                // Map vertical mouse movement to offset change along the segment
                double dy = e.getSceneY() - dragStartY;
                double segLen = mod.segment.getLength() * SCALE;
                // Approximate: each pixel ≈ some fraction of segment length
                double sensitivity = 0.003; // tune this
                double newOffset = dragStartOffset + dy * sensitivity;
                newOffset = Math.max(0.0, Math.min(1.0, newOffset));
                mod.offsetFraction = newOffset;

                // Update sensor's internal offset if it's a sensor
                if (mod.type == DraggableModule.ModuleType.SENSOR && mod.sensor != null) {
                    mod.sensor.setLocalOffsetFraction(newOffset);
                }

                // Immediately update the visual position
                updateModulePosition(mod);
                updateModuleInfoLabel(mod);
            }
        });

        node.setOnMouseReleased((MouseEvent e) -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                e.consume();
            }
        });

        // Hover effect — use material change only, NOT scale (scale shifts bounds causing oscillation)
        node.setOnMouseEntered(e -> {
            if (mod != selectedModule) {
                if (mod.type == DraggableModule.ModuleType.MOTOR) {
                    ((Box) node).setMaterial(motorHoverMaterial);
                } else {
                    ((Sphere) node).setMaterial(sensorHoverMaterial);
                }
            }
        });
        node.setOnMouseExited(e -> {
            if (mod != selectedModule) {
                if (mod.type == DraggableModule.ModuleType.MOTOR) {
                    ((Box) node).setMaterial(motorMaterial);
                } else {
                    ((Sphere) node).setMaterial(sensorMaterial);
                }
            }
        });
    }

    private void selectModule(DraggableModule mod) {
        // Deselect previous
        if (selectedModule != null) {
            if (selectedModule.type == DraggableModule.ModuleType.MOTOR) {
                ((Box) selectedModule.node).setMaterial(motorMaterial);
            } else {
                ((Sphere) selectedModule.node).setMaterial(sensorMaterial);
            }
        }

        selectedModule = mod;
        if (mod.type == DraggableModule.ModuleType.MOTOR) {
            ((Box) mod.node).setMaterial(motorSelectedMaterial);
        } else {
            ((Sphere) mod.node).setMaterial(sensorSelectedMaterial);
        }
        updateModuleInfoLabel(mod);
    }

    private void updateModuleInfoLabel(DraggableModule mod) {
        String info;
        if (mod.type == DraggableModule.ModuleType.MOTOR && mod.motor != null) {
            info = String.format("🔧 %s | Segment: %s | Offset: %.0f%% | MaxTorque: %.0f N·m",
                    mod.label, mod.segment.getName(), mod.offsetFraction * 100,
                    mod.motor.getMaxTorque());
        } else if (mod.sensor != null) {
            info = String.format("📡 %s | Segment: %s | Offset: %.0f%%",
                    mod.label, mod.segment.getName(), mod.offsetFraction * 100);
        } else {
            info = mod.label;
        }
        moduleInfoLabel.setText(info);
        moduleInfoLabel.setVisible(true);
    }

    // ====================================================================
    // Transform updates (called every frame)
    // ====================================================================

    public void updateTransforms(SimulationState state) {
        HumanModel human = state.getHumanModel();

        // Update human + exo segment positions
        positionCylinder(thighCyl, human.getThigh());
        positionCylinder(shankCyl, human.getShank());
        positionCylinder(footCyl,  human.getFoot());

        positionCylinder(exoThighCyl, human.getThigh());
        positionCylinder(exoShankCyl, human.getShank());
        positionCylinder(exoFootCyl,  human.getFoot());

        // Joint spheres at joint positions
        setPos(hipSphere, human.getHipAnchorX(), human.getHipAnchorY());
        setPos(kneeSphere, human.getThigh().getDistalX(), human.getThigh().getDistalY());
        setPos(ankleSphere, human.getShank().getDistalX(), human.getShank().getDistalY());

        // Update draggable module positions
        for (DraggableModule mod : draggableModules) {
            updateModulePosition(mod);
        }

        // Color joints based on safety
        updateSafetyColors(state);
    }

    /**
     * Positions a cylinder so it spans from the segment's proximal end to its distal end.
     * The cylinder's center is at the midpoint, rotated by the segment's angle.
     */
    private void positionCylinder(Cylinder cyl, RigidBodySegment seg) {
        double len = seg.getLength() * SCALE;
        // We negate the angle because JavaFX's Y-axis is inverted (downwards)
        double angleDeg = -Math.toDegrees(seg.getAngle());

        // 1. Joint/Pivot position (the "ball")
        double jx = seg.getPosX() * SCALE;
        double jy = -seg.getPosY() * SCALE; // Y-inversion for JavaFX coordinates

        cyl.setHeight(len);
        cyl.getTransforms().clear();

        // --- TRANSFORMATION ORDER IS KEY (Applied Last to First) ---

        // T3: Move the entire assembly to the joint's world position
        Translate moveToJoint = new Translate(jx, jy, 0);

        // T2: Rotate around the pivot.
        // Since T1 has shifted the cylinder, the pivot is now at the top!
        Rotate rotateAroundTop = new Rotate(angleDeg, 0, 0, 0, Rotate.Z_AXIS);

        // T1: Offset the cylinder downwards by half its length (H/2)
        // This aligns the "TOP" of the cylinder with the local (0,0,0) origin
        Translate offsetToTop = new Translate(0, len / 2, 0);

        // Important: addAll order matters. JavaFX applies these in reverse order
        // to achieve the "Move -> Rotate -> Offset" logic.
        cyl.getTransforms().addAll(moveToJoint, rotateAroundTop, offsetToTop);
    }

    /**
     * Sets a node's position in JavaFX 3D space from simulation coords.
     * Simulation: +Y up. JavaFX 3D: +Y down.
     */
    private void setPos(Node node, double simX, double simY) {
        node.setTranslateX(simX * SCALE);
        node.setTranslateY(-simY * SCALE);
        node.setTranslateZ(0);
    }

    /**
     * Positions a draggable module along its segment based on offsetFraction.
     */
    private void updateModulePosition(DraggableModule mod) {
        RigidBodySegment seg = mod.segment;
        double px = seg.getPosX();
        double py = seg.getPosY();
        double angle = seg.getAngle();
        double frac = mod.offsetFraction;

        // Position along the segment: lerp from proximal to distal
        double wx = px + frac * seg.getLength() * Math.sin(angle);
        double wy = py - frac * seg.getLength() * Math.cos(angle);

        // Offset slightly on Z axis so modules are visible in front of the segment
        double zOffset = (mod.type == DraggableModule.ModuleType.MOTOR) ? 0.04 : -0.04;

        // Use setTranslate instead of transforms list to avoid flicker
        mod.node.setTranslateX(wx * SCALE);
        mod.node.setTranslateY(-wy * SCALE);
        mod.node.setTranslateZ(zOffset);
    }

    // ====================================================================
    // Safety colors
    // ====================================================================

    private void updateSafetyColors(SimulationState state) {
        List<SimulationState.SafetyViolation> violations = state.getSafetyViolations();
        boolean hipDanger = false, kneeDanger = false, ankleDanger = false;

        double recentThreshold = state.getTime() - 0.1;
        for (int i = violations.size() - 1; i >= 0 && i >= violations.size() - 50; i--) {
            SimulationState.SafetyViolation v = violations.get(i);
            if (v.time() < recentThreshold) break;
            switch (v.jointName()) {
                case "Hip"   -> hipDanger = true;
                case "Knee"  -> kneeDanger = true;
                case "Ankle" -> ankleDanger = true;
            }
        }

        hipSphere.setMaterial(hipDanger ? dangerMaterial : jointMaterial);
        kneeSphere.setMaterial(kneeDanger ? dangerMaterial : jointMaterial);
        ankleSphere.setMaterial(ankleDanger ? dangerMaterial : jointMaterial);
    }

    // ====================================================================
    // Floor grid
    // ====================================================================

    private Group createFloorGrid() {
        Group grid = new Group();
        PhongMaterial gridMat = new PhongMaterial(Color.web("#333333"));
        for (int i = -5; i <= 5; i++) {
            Box lineX = new Box(10 * SCALE, 0.002, 0.002);
            lineX.setTranslateZ(i * 0.2 * SCALE);
            lineX.setMaterial(gridMat);
            Box lineZ = new Box(0.002, 0.002, 10 * SCALE);
            lineZ.setTranslateX(i * 0.2 * SCALE);
            lineZ.setMaterial(gridMat);
            grid.getChildren().addAll(lineX, lineZ);
        }
        return grid;
    }

    // ====================================================================
    // Simulation step callback
    // ====================================================================

    private void onSimulationStep(SimulationState state) {
        updateTransforms(state);
        controlPanel.updateTime(state.getTime());
        dataPanel.update(state);
        chartPanel.update(state);
    }

    // ====================================================================
    // Draggable module record
    // ====================================================================

    /**
     * Represents a draggable exoskeleton module (motor or sensor) in the 3D viewport.
     * Users can click and drag to reposition modules along their segment.
     */
    private static class DraggableModule {
        enum ModuleType { MOTOR, SENSOR }

        final String label;
        final Node node;
        final RigidBodySegment segment;
        double offsetFraction; // 0 = proximal, 1 = distal
        final ModuleType type;
        final Motor motor;     // non-null if type == MOTOR
        final ImuSensor sensor; // non-null if type == SENSOR

        DraggableModule(String label, Node node, RigidBodySegment segment, double offsetFraction,
                        ModuleType type, Motor motor, ImuSensor sensor) {
            this.label = label;
            this.node = node;
            this.segment = segment;
            this.offsetFraction = offsetFraction;
            this.type = type;
            this.motor = motor;
            this.sensor = sensor;
        }
    }
    // ====================================================================
    // CSV IMPORT / EXPORT LOGIC ---
    // ====================================================================
    private void handleImport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import(CSV)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line = br.readLine();
                if ((line = br.readLine()) != null) {
                    String[] vals = line.split(",");
                    HumanModel human = engine.getState().getHumanModel();
                    ExoskeletonModel exo = engine.getState().getExoskeletonModel();
                    human.setHeight(Double.parseDouble(vals[0]));
                    human.setTotalMass(Double.parseDouble(vals[1]));
                    exo.setMotorMaxTorque(Double.parseDouble(vals[2]));
                    exo.setMotorMaxPower(Double.parseDouble(vals[3]));
                    human.enforcePositionConstraints();
                    updateTransforms(engine.getState());
                    controlPanel.updateSliders();
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }

    }

    private void handleExport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export (CSV)");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH'h'mm");
        String timestamp = LocalDateTime.now().format(formatter);

        fc.setInitialFileName("exo_params_" + timestamp + ".csv");

        File file = fc.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try (PrintWriter pw = new PrintWriter(file)) {
                pw.println("Height,Mass,MaxMotorTorque,MaxMotorPower");
                HumanModel human = engine.getState().getHumanModel();
                ExoskeletonModel exo = engine.getState().getExoskeletonModel();
                pw.printf("%.3f,%.3f,%.3f,%.3f\n",
                        human.getHeight(), human.getTotalMass(), exo.getMotorMaxTorque(), exo.getMotorMaxPower());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    // ====================================================================
    // Getters
    // ====================================================================

    public ControlPanel getControlPanel() { return controlPanel; }
    public DataPanel getDataPanel() { return dataPanel; }
    public ChartPanel getChartPanel() { return chartPanel; }
    public CameraController getCameraController() { return cameraController; }
}
