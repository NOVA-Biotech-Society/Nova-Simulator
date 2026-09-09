package simulation.view;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import simulation.app.MainApp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs against the real JavaFX scene graph under CI's virtual display; never opens a device. */
class MotionCaptureViewTest {
    @Test void startsAndExpandsCaptureAtSupportedWindowSizesWithoutEnablingCamera() throws Exception {
        assumeTrue("true".equals(System.getenv("NOVA_FX_SMOKE")), "Set NOVA_FX_SMOKE=true with a graphical display");
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.startup(() -> Platform.setImplicitExit(false));
        Platform.runLater(() -> {
            MainApp app = new MainApp();
            Stage stage = new Stage();
            try {
                app.start(stage);
                Scene scene = stage.getScene();
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                ToggleButton camera = button(scene, "Enable camera");
                ToggleButton pose = button(scene, "Enable MediaPipe");
                ToggleButton panel = button(scene, "Motion capture");
                assertFalse(camera.isSelected());
                assertFalse(pose.isSelected());
                panel.fire();
                for (double[] size : new double[][]{{900,600},{1280,720}}) {
                    stage.setWidth(size[0]); stage.setHeight(size[1]);
                    scene.getRoot().applyCss(); scene.getRoot().layout();
                    var capture = scene.lookup(".capture-panel");
                    assertNotNull(capture);
                    assertTrue(capture.isVisible());
                    assertTrue(capture.getBoundsInParent().getWidth() >= 270);
                    assertTrue(scene.snapshot(null).getWidth() > 0, "Scene must render successfully");
                    assertFalse(camera.isSelected(), "Opening the panel must not start capture");
                    assertFalse(pose.isSelected());
                }
                panel.fire();
                assertFalse(panel.isSelected());
                result.complete(null);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            } finally {
                app.stop(); stage.close(); Platform.exit();
            }
        });
        result.get(30, TimeUnit.SECONDS);
    }
    private static ToggleButton button(Scene scene, String text) {
        return scene.lookupAll(".toggle-button").stream()
                .filter(node -> node instanceof ToggleButton button && text.equals(button.getText()))
                .map(node -> (ToggleButton)node).findFirst().orElseThrow();
    }
}
