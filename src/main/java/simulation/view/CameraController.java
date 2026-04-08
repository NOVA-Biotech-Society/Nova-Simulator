package simulation.view;

import javafx.animation.AnimationTimer;
import javafx.scene.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * Provides orbit, zoom, and pan camera controls for a 3D SubScene.
 * <p>
 * Controls:
 * <ul>
 *   <li><b>Left drag</b>: orbit (rotate camera around pivot)</li>
 *   <li><b>Scroll</b>: zoom in/out</li>
 *   <li><b>Right drag / Shift+drag</b>: pan</li>
 * </ul>
 * </p>
 */
public class CameraController {

    public enum ViewMode {
        FRONT,
        SIDE,
        SPIN_360,
        CINEMATIC
    }

    private final Group cameraRig;
    private final PerspectiveCamera camera;
    private final Translate pivot;
    private final Rotate rotateX;
    private final Rotate rotateY;
    private final Translate zoom;

    private double mouseOldX, mouseOldY;
    private ViewMode currentViewMode = ViewMode.SIDE;
    private AnimationTimer modeAnimation;
    private long modeStartNanos;

    // Default values
    private static final double DEFAULT_DISTANCE = -3.0;
    private static final double DEFAULT_ANGLE_X = -20;
    private static final double DEFAULT_ANGLE_Y = 0;
    private static final double DEFAULT_PIVOT_Y = -0.8;

    /**
     * Creates a camera controller and attaches it to the given SubScene.
     *
     * @param subScene the SubScene to control
     * @return the configured PerspectiveCamera
     */
    public CameraController(SubScene subScene) {
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.01);
        camera.setFarClip(100.0);
        camera.setFieldOfView(45);

        // Camera rig hierarchy: pivot → rotateY → rotateX → zoom → camera
        pivot = new Translate(0, DEFAULT_PIVOT_Y, 0);
        rotateY = new Rotate(DEFAULT_ANGLE_Y, Rotate.Y_AXIS);
        rotateX = new Rotate(DEFAULT_ANGLE_X, Rotate.X_AXIS);
        zoom = new Translate(0, 0, DEFAULT_DISTANCE);

        cameraRig = new Group();
        cameraRig.getTransforms().addAll(pivot, rotateY, rotateX, zoom);
        cameraRig.getChildren().add(camera);

        subScene.setCamera(camera);

        // Mouse handlers
        subScene.setOnMousePressed(event -> {
            mouseOldX = event.getSceneX();
            mouseOldY = event.getSceneY();
        });

        subScene.setOnMouseDragged(event -> {
            double dx = event.getSceneX() - mouseOldX;
            double dy = event.getSceneY() - mouseOldY;
            mouseOldX = event.getSceneX();
            mouseOldY = event.getSceneY();

            if (event.getButton() == MouseButton.PRIMARY && !event.isShiftDown()) {
                // Orbit
                rotateY.setAngle(rotateY.getAngle() + dx * 0.3);
                rotateX.setAngle(rotateX.getAngle() - dy * 0.3);
            } else if (event.getButton() == MouseButton.SECONDARY || event.isShiftDown()) {
                // Pan
                pivot.setX(pivot.getX() + dx * 0.002);
                pivot.setY(pivot.getY() + dy * 0.002);
            }
        });

        subScene.addEventHandler(ScrollEvent.SCROLL, event -> {
            double delta = event.getDeltaY();
            zoom.setZ(zoom.getZ() + delta * 0.005);
        });

        // Match initial defaults with a named mode.
        setViewMode(ViewMode.SIDE);
    }

    /** Returns the camera rig group (add this to the 3D scene root). */
    public Group getCameraRig() { return cameraRig; }

    /** Returns the camera. */
    public PerspectiveCamera getCamera() { return camera; }

    /** Resets camera to a side view of the leg. */
    public void resetToSideView() {
        stopModeAnimation();
        currentViewMode = ViewMode.SIDE;
        pivot.setX(0);
        pivot.setY(DEFAULT_PIVOT_Y);
        pivot.setZ(0);
        rotateX.setAngle(DEFAULT_ANGLE_X);
        rotateY.setAngle(0);
        zoom.setZ(DEFAULT_DISTANCE);
    }

    /** Resets camera to a front view. */
    public void resetToFrontView() {
        stopModeAnimation();
        currentViewMode = ViewMode.FRONT;
        pivot.setX(0);
        pivot.setY(DEFAULT_PIVOT_Y);
        pivot.setZ(0);
        rotateX.setAngle(DEFAULT_ANGLE_X);
        rotateY.setAngle(90);
        zoom.setZ(DEFAULT_DISTANCE);
    }

    public void setViewMode(ViewMode mode) {
        if (mode == null) {
            mode = ViewMode.SIDE;
        }
        currentViewMode = mode;
        switch (mode) {
            case FRONT -> resetToFrontView();
            case SIDE -> resetToSideView();
            case SPIN_360 -> startSpin360Mode();
            case CINEMATIC -> startCinematicMode();
        }
    }

    public ViewMode getCurrentViewMode() {
        return currentViewMode;
    }

    public void cycleViewMode() {
        ViewMode[] modes = ViewMode.values();
        int next = (currentViewMode.ordinal() + 1) % modes.length;
        setViewMode(modes[next]);
    }

    private void startSpin360Mode() {
        stopModeAnimation();
        pivot.setX(0);
        pivot.setY(DEFAULT_PIVOT_Y);
        pivot.setZ(0);
        rotateX.setAngle(-18);
        zoom.setZ(-3.2);
        modeStartNanos = System.nanoTime();
        modeAnimation = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double t = (now - modeStartNanos) / 1_000_000_000.0;
                rotateY.setAngle((t * 15.0) % 360.0); // Slow cinematic orbit.
                rotateX.setAngle(-18 + 2.5 * Math.sin(t * 0.35));
            }
        };
        modeAnimation.start();
    }

    private void startCinematicMode() {
        stopModeAnimation();
        modeStartNanos = System.nanoTime();
        modeAnimation = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double t = (now - modeStartNanos) / 1_000_000_000.0;
                double cycle = t % 24.0;

                // Four 6-second shot blocks: front close, side mid, rear far, overhead-ish arc.
                int shotIndex = (int) (cycle / 6.0);
                double shotT = (cycle % 6.0) / 6.0;

                switch (shotIndex) {
                    case 0 -> applyCinematicShot(90, -14, -2.4, 0.0, DEFAULT_PIVOT_Y + 0.04, 0.0, shotT, t);
                    case 1 -> applyCinematicShot(10, -20, -3.1, 0.03, DEFAULT_PIVOT_Y + 0.02, 0.0, shotT, t);
                    case 2 -> applyCinematicShot(210, -16, -3.7, -0.03, DEFAULT_PIVOT_Y, 0.0, shotT, t);
                    default -> applyCinematicShot(320, -30, -2.8, 0.0, DEFAULT_PIVOT_Y - 0.02, 0.0, shotT, t);
                }
            }
        };
        modeAnimation.start();
    }

    private void applyCinematicShot(double targetY, double targetX, double targetZoom,
                                    double targetPivotX, double targetPivotY, double targetPivotZ,
                                    double shotT, double globalT) {
        // Ease in/out per shot and add very small breathing motion.
        double eased = 0.5 - 0.5 * Math.cos(Math.PI * Math.max(0, Math.min(1, shotT)));
        rotateY.setAngle(targetY + 4.0 * Math.sin(globalT * 0.25));
        rotateX.setAngle(targetX + 1.5 * Math.sin(globalT * 0.4));
        zoom.setZ(targetZoom + 0.06 * Math.sin(globalT * 0.7));
        pivot.setX(targetPivotX + 0.015 * Math.sin(globalT * 0.35) * eased);
        pivot.setY(targetPivotY + 0.01 * Math.cos(globalT * 0.32) * eased);
        pivot.setZ(targetPivotZ);
    }

    private void stopModeAnimation() {
        if (modeAnimation != null) {
            modeAnimation.stop();
            modeAnimation = null;
        }
    }
}
