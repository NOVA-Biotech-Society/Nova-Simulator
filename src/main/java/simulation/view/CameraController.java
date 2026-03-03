package simulation.view;

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

    private final Group cameraRig;
    private final PerspectiveCamera camera;
    private final Translate pivot;
    private final Rotate rotateX;
    private final Rotate rotateY;
    private final Translate zoom;

    private double mouseOldX, mouseOldY;

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
    }

    /** Returns the camera rig group (add this to the 3D scene root). */
    public Group getCameraRig() { return cameraRig; }

    /** Returns the camera. */
    public PerspectiveCamera getCamera() { return camera; }

    /** Resets camera to a side view of the leg. */
    public void resetToSideView() {
        pivot.setX(0);
        pivot.setY(DEFAULT_PIVOT_Y);
        pivot.setZ(0);
        rotateX.setAngle(DEFAULT_ANGLE_X);
        rotateY.setAngle(0);
        zoom.setZ(DEFAULT_DISTANCE);
    }

    /** Resets camera to a front view. */
    public void resetToFrontView() {
        pivot.setX(0);
        pivot.setY(DEFAULT_PIVOT_Y);
        pivot.setZ(0);
        rotateX.setAngle(DEFAULT_ANGLE_X);
        rotateY.setAngle(90);
        zoom.setZ(DEFAULT_DISTANCE);
    }
}

