package simulation.view;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import simulation.app.MainApp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real JavaFX pulses catch preferred/minimum-size feedback that a single layout misses. */
class MotionCaptureViewTest {
    @Test void captureStaysStableAcrossPulsesImagesAndWindowResizes() throws Exception {
        assumeTrue("true".equals(System.getenv("NOVA_FX_SMOKE")), "Set NOVA_FX_SMOKE=true with a graphical display");
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.startup(() -> Platform.setImplicitExit(false));
        Platform.runLater(() -> {
            MainApp app = new MainApp();
            Stage stage = new Stage();
            try {
                app.start(stage);
                Scene scene = stage.getScene();
                scene.getRoot().applyCss(); scene.getRoot().layout();
                ToggleButton camera = button(scene, "Enable camera");
                ToggleButton pose = button(scene, "Enable MediaPipe");
                ToggleButton panel = button(scene, "Motion capture");
                AnimationTimer check = new AnimationTimer() {
                    int phase, pulse;
                    double[] baseline;
                    final double[][] sizes = {{900,600},{1280,720},{900,600},{1280,720}};
                    @Override public void handle(long now) {
                        try {
                            if (pulse == 0) {
                                if (phase == 0 || phase == 3) panel.fire();
                                stage.setWidth(sizes[phase][0]); stage.setHeight(sizes[phase][1]);
                            }
                            pulse++;
                            if (phase < 3 && pulse == 5) {
                                StackPane preview = (StackPane) scene.lookup(".capture-preview");
                                ImageView image = (ImageView) preview.getChildren().stream()
                                        .filter(ImageView.class::isInstance).findFirst().orElseThrow();
                                image.setImage(new WritableImage(phase == 1 ? 480 : 1280, 720));
                            }
                            if (pulse >= 20) {
                                double[] current = geometry(scene, stage);
                                if (pulse == 20) baseline = current;
                                for (int i = 0; i < current.length; i++)
                                    assertEquals(baseline[i], current[i], i == 4 || i == 6 ? 0.002 : 1.0,
                                            "Layout drift at phase " + phase + ", pulse " + pulse + ", coordinate " + i);
                                assertTrue(scene.getWidth() <= sizes[phase][0] + 1, "Window must shrink to the requested size");
                                assertFalse(camera.isSelected()); assertFalse(pose.isSelected());
                                if (phase < 3) {
                                    StackPane preview = (StackPane) scene.lookup(".capture-preview");
                                    Canvas overlay = (Canvas) preview.getChildren().stream()
                                            .filter(Canvas.class::isInstance).findFirst().orElseThrow();
                                    assertFalse(overlay.isManaged());
                                    assertTrue(overlay.getWidth() <= preview.getWidth() - 1,
                                            "Overlay must fit inside the preview border");
                                }
                            }
                            if (pulse == 70) {
                                assertTrue(scene.snapshot(null).getWidth() > 0);
                                if (++phase == sizes.length) {
                                    stop(); app.stop(); stage.close(); Platform.exit(); result.complete(null);
                                } else { pulse = 0; baseline = null; }
                            }
                        } catch (Throwable error) {
                            stop(); app.stop(); stage.close(); Platform.exit(); result.completeExceptionally(error);
                        }
                    }
                };
                check.start();
            } catch (Throwable error) {
                app.stop(); stage.close(); Platform.exit(); result.completeExceptionally(error);
            }
        });
        result.get(45, TimeUnit.SECONDS);
    }
    private static double[] geometry(Scene scene, Stage stage) {
        var main = (simulation.view.MainView) scene.getRoot();
        SplitPane outer = (SplitPane) main.getCenter();
        SplitPane inner = (SplitPane) ((javafx.scene.layout.BorderPane) outer.getItems().get(0)).getCenter();
        var viewport = inner.getItems().get(inner.getItems().size() - 1);
        return new double[]{stage.getX(), stage.getY(), scene.getWidth(), scene.getHeight(),
                outer.getDividerPositions()[0], outer.getItems().get(1).localToScene(0, 0).getX(),
                inner.getDividerPositions().length == 0 ? 0 : inner.getDividerPositions()[0],
                viewport.localToScene(0, 0).getX(), viewport.getLayoutBounds().getWidth()};
    }
    private static ToggleButton button(Scene scene, String text) {
        return scene.getRoot().lookupAll(".toggle-button").stream()
                .filter(node -> node instanceof ToggleButton button && text.equals(button.getText()))
                .map(node -> (ToggleButton)node).findFirst().orElseThrow();
    }
}
