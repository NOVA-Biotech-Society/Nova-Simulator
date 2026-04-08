package simulation.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import simulation.controller.ExoController;
import simulation.controller.ScriptedProstrationController;
import simulation.model.ExoskeletonModel;
import simulation.model.HumanModel;
import simulation.model.SimulationState;
import simulation.physics.SimulationEngine;
import simulation.view.MainView;

/**
 * Application entry point for the Nova Exoskeleton Simulator.
 * <p>
 * Wires together the simulation model, physics engine, controller, and UI.
 * </p>
 *
 * <h3>Architecture:</h3>
 * <pre>
 *     SimulationConfig
 *         ↓
 *     HumanModel + ExoskeletonModel
 *         ↓
 *     SimulationState
 *         ↓
 *     SimulationEngine ← ExoController
 *         ↓
 *     MainView (3D + UI panels)
 * </pre>
 */
public class MainApp extends Application {

    private SimulationEngine engine;
    private MainView mainView;

    @Override
    public void start(Stage primaryStage) {
        // 1. Load configuration
        SimulationConfig config = SimulationConfig.defaultConfig();

        // 2. Build models
        HumanModel humanModel = new HumanModel(config.getHumanHeight(), config.getHumanMass());
        ExoskeletonModel exoModel = new ExoskeletonModel(
                humanModel,
                config.getExoMassFraction(),
                config.getMotorMaxTorque(),
                config.getMotorMaxPower()
        );

        // 3. Create simulation state
        SimulationState state = new SimulationState(humanModel, exoModel, config.getDt());
        state.reset(); // Initialize to standing pose

        // 4. Create controller
        ExoController controller = new ScriptedProstrationController();

        // 5. Create simulation engine
        engine = new SimulationEngine(state, controller);

        // 6. Build UI
        mainView = new MainView(engine);

        // 7. Create scene and show
        Scene scene = new Scene(mainView, 1280, 720);
        scene.setFill(javafx.scene.paint.Color.web("#1a1a2e"));

        primaryStage.setTitle("Nova Exoskeleton Simulator");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();

        System.out.println("Nova Simulator started with config: " + config);
        System.out.println("Controller: " + controller.getName());
    }

    @Override
    public void stop() {
        // Clean shutdown of the simulation thread
        if (engine != null) {
            engine.pause();
        }
        if (mainView != null) {
            mainView.shutdown();
        }
        System.out.println("Nova Simulator stopped.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
