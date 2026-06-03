package simulation.physics;

import javafx.application.Platform;
import simulation.controller.ExoController;
import simulation.controller.MotorCommands;
import simulation.model.ExoskeletonModel;
import simulation.model.Motor;
import simulation.model.SimulationState;
import simulation.model.RigidBodySegment;
import simulation.model.HumanModel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The main simulation engine that drives the physics loop.
 * <p>
 * This version anchors the FOOT to the ground. Instead of allowing the foot to move
 * based on the hip, it measures how much the foot moved locally, inverts that displacement,
 * and shifts the hip anchor. This makes the foot the absolute world anchor point.
 * </p>
 */
public class SimulationEngine {

    private final SimulationState state;
    private final PhysicsIntegrator integrator;
    private final SafetyEvaluator safetyEvaluator;
    private ExoController controller;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> simulationTask;

    private Consumer<SimulationState> onStepCallback;
    private double speedMultiplier = 1.0;
    private int stepsPerUpdate = 10;
    private int stepCounter = 0;
    private boolean currentStepDangerous = false;

    // The desired world ground height
    private static final double GROUND_HEIGHT = 0.0;

    public SimulationEngine(SimulationState state, ExoController controller) {
        this.state = state;
        this.integrator = new PhysicsIntegrator();
        this.safetyEvaluator = new SafetyEvaluator();
        this.controller = controller;
    }

    /**
     * Performs a single simulation step where the foot acts as the anchor.
     */
    public void step() {
        double dt = state.getDt();
        HumanModel human = state.getHumanModel();

        // Enforce skeletal alignment before applying physics
        human.enforcePositionConstraints();

        // 1. Compute motor commands from controller
        MotorCommands cmds = controller.computeCommands(state, state.getTime());

        // 2. Clear forces, then apply motor commands
        ExoskeletonModel exo = state.getExoskeletonModel();
        for (var seg : human.getAllSegments()) {
            seg.clearForces();
        }

        // Apply your torques (keeping your inverted hip torque if your controller requires it)
        exo.getHipMotor().applyCommand(cmds.hipTorque());
        exo.getKneeMotor().applyCommand(cmds.kneeTorque());
        exo.getAnkleMotor().applyCommand(cmds.ankleTorque());


        for (Motor motor : exo.getAllMotors()) {
            motor.applyToJoint();
        }

        // --- STEP A: SNAPSHOT THE FOOT'S POSITION BEFORE WE MOVE THE JOINTS ---
        RigidBodySegment foot = human.getFoot();
        double footXBefore = foot.getDistalX();
        double footYBefore = foot.getDistalY();

        // 3. Integrate physics (This updates joint angles based on your torques)
        integrator.integrate(state, dt);

        // Re-align bones (this will temporarily move the foot because hipAnchor is still at its old position)
        human.enforcePositionConstraints();

        // 4. Update sensors
        exo.updateSensors(dt, state.getTime());

        // --- STEP B: FORCE THE FOOT TO BE THE ANCHOR (KINEMATIC INVERSION) ---
        // Calculate how much the joint rotations tried to move the foot in space
        double deltaX = foot.getDistalX() - footXBefore;
        double deltaY = foot.getDistalY() - footYBefore;

        // INVERSION: Instead of moving the foot forward/downward, we apply the
        // EXACT OPPOSITE movement to the hip anchor.
        // This pins the foot instantly in the world and force-moves the hip instead!
        human.setHipAnchorX(human.getHipAnchorX() - deltaX);
        human.setHipAnchorY(human.getHipAnchorY() - deltaY);

        // --- STEP C: GROUND LOCK SNAP ---
        // Ensure that the bottom of the foot stays exactly glued to the floor line (Y = 0)
        double correctionY = GROUND_HEIGHT - foot.getDistalY();
        human.setHipAnchorY(human.getHipAnchorY() + correctionY);

        // CRITICAL: Re-enforce constraints right now so that the thigh, shank, and foot
        // are drawn relative to our newly calculated walking/squatting hip position!
        human.enforcePositionConstraints();

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

        long periodMicros = (long) (state.getDt() * 1_000_000 / speedMultiplier);
        periodMicros = Math.max(100, periodMicros);

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

    /** Advances the simulation by one step. */
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
    public void setController(ExoController controller) { this.controller = controller; }
    public void setOnStepCallback(Consumer<SimulationState> callback) { this.onStepCallback = callback; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public void setSpeedMultiplier(double speedMultiplier) {
        this.speedMultiplier = Math.max(0.1, Math.min(10.0, speedMultiplier));
        if (running.get()) {
            pause();
            play();
        }
    }
    public void setStepsPerUpdate(int steps) { this.stepsPerUpdate = Math.max(1, steps); }
}