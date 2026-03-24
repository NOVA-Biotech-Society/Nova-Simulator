package simulation.physics;

import javafx.application.Platform;
import simulation.controller.ExoController;
import simulation.controller.MotorCommands;
import simulation.model.ExoskeletonModel;
import simulation.model.Motor;
import simulation.model.SimulationState;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The main simulation engine that drives the physics loop.
 * <p>
 * Runs on a background thread using a {@link ScheduledExecutorService} and posts
 * state updates to the JavaFX Application Thread via {@link Platform#runLater}.
 * </p>
 *
 * <h3>Loop structure (each step):</h3>
 * <ol>
 *   <li>Compute motor commands from the controller.</li>
 *   <li>Apply motor commands to joints.</li>
 *   <li>Integrate physics (forces, velocities, positions).</li>
 *   <li>Update sensors.</li>
 *   <li>Evaluate safety.</li>
 *   <li>Advance time.</li>
 *   <li>Post UI update callback.</li>
 * </ol>
 */
public class SimulationEngine {

    private final SimulationState state;
    private final PhysicsIntegrator integrator;
    private final SafetyEvaluator safetyEvaluator;
    private ExoController controller;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> simulationTask;

    // Callback invoked on the JavaFX thread after each step (for UI updates)
    private Consumer<SimulationState> onStepCallback;

    // Simulation speed multiplier (1.0 = real-time, 2.0 = double speed)
    private double speedMultiplier = 1.0;

    // Steps per UI update (to avoid flooding the FX thread)
    private int stepsPerUpdate = 10;

    // Step counter for throttled UI updates
    private int stepCounter = 0;

    // Track whether there's a pending danger in the current step
    private boolean currentStepDangerous = false;

    /**
     * Creates a new simulation engine.
     *
     * @param state      the simulation state to drive
     * @param controller the controller providing motor commands
     */
    public SimulationEngine(SimulationState state, ExoController controller) {
        this.state = state;
        this.integrator = new PhysicsIntegrator();
        this.safetyEvaluator = new SafetyEvaluator();
        this.controller = controller;
    }

    /**
     * Performs a single simulation step.
     */
    public void step() {
        double dt = state.getDt();

        // 1. Compute motor commands from controller
        MotorCommands cmds = controller.computeCommands(state, state.getTime());

        // 2. Clear forces, then apply motor commands
        ExoskeletonModel exo = state.getExoskeletonModel();
        // Clear forces FIRST so motor torques are not wiped
        for (var seg : state.getHumanModel().getAllSegments()) {
            seg.clearForces();
        }

        exo.getHipMotor().applyCommand(cmds.hipTorque());
        exo.getKneeMotor().applyCommand(cmds.kneeTorque());
        exo.getAnkleMotor().applyCommand(cmds.ankleTorque());

        // Apply motor torques to segments
        for (Motor motor : exo.getAllMotors()) {
            motor.applyToJoint();
        }

        // 3. Integrate physics (gravity, constraints, integration — no clearForces inside)
        integrator.integrate(state, dt);
        state.getHumanModel().enforcePositionConstraints();
        // 4. Update sensors
        exo.updateSensors(dt, state.getTime());

        // 5. Evaluate safety
        currentStepDangerous = safetyEvaluator.evaluate(state);

        // 6. Advance time
        state.advanceTime();

        // 7. Throttled UI update
        stepCounter++;
        if (stepCounter >= stepsPerUpdate && onStepCallback != null) {
            stepCounter = 0;
            Platform.runLater(() -> onStepCallback.accept(state));
        }
    }

    /** Starts the simulation loop on a background thread. */
    public void play() {
        if (running.get()) return;
        running.set(true);

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SimulationEngine");
            t.setDaemon(true);
            return t;
        });

        // Schedule at the physics rate, adjusted for speed multiplier
        long periodMicros = (long) (state.getDt() * 1_000_000 / speedMultiplier);
        periodMicros = Math.max(100, periodMicros); // minimum 100 µs

        simulationTask = executor.scheduleAtFixedRate(() -> {
            try {
                if (running.get()) {
                    step();
                }
            } catch (Exception e) {
                e.printStackTrace();
                running.set(false);
            }
        }, 0, periodMicros, TimeUnit.MICROSECONDS);
    }

    /** Pauses the simulation. */
    public void pause() {
        running.set(false);
        if (simulationTask != null) {
            simulationTask.cancel(false);
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    /** Resets the simulation to initial conditions. */
    public void reset() {
        pause();
        state.reset();
        controller.reset();
        stepCounter = 0;
        if (onStepCallback != null) {
            Platform.runLater(() -> onStepCallback.accept(state));
        }
    }

    /** Advances the simulation by one step (for step-by-step debugging). */
    public void singleStep() {
        step();
        if (onStepCallback != null) {
            Platform.runLater(() -> onStepCallback.accept(state));
        }
    }

    // ---- Getters & setters ----

    public boolean isRunning() { return running.get(); }
    public SimulationState getState() { return state; }
    public ExoController getController() { return controller; }

    public void setController(ExoController controller) {
        this.controller = controller;
    }

    public void setOnStepCallback(Consumer<SimulationState> callback) {
        this.onStepCallback = callback;
    }

    public double getSpeedMultiplier() { return speedMultiplier; }
    public void setSpeedMultiplier(double speedMultiplier) {
        this.speedMultiplier = Math.max(0.1, Math.min(10.0, speedMultiplier));
        // If running, restart with new timing
        if (running.get()) {
            pause();
            play();
        }
    }

    public void setStepsPerUpdate(int steps) {
        this.stepsPerUpdate = Math.max(1, steps);
    }
}



