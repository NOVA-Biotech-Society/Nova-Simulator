package simulation.controller;

import simulation.model.Joint;
import simulation.model.SimulationState;
import java.util.ArrayList;
import java.util.List;

/**
 * Un contrôleur basé sur une liste de Keyframes dynamiques pour l'animation du Sujood.
 * Il est maintenant super facile d'ajouter, modifier ou retirer des étapes !
 */
public class ScriptedProstrationController implements ExoController {


    private static class Keyframe {
        final String name;
        final double time;
        final double hipAngle;
        final double kneeAngle;
        final double ankleAngle;

        Keyframe(String name, double time, double hipDeg, double kneeDeg, double ankleDeg) {
            this.name = name;
            this.time = time;
            this.hipAngle = Math.toRadians(hipDeg);
            this.kneeAngle = Math.toRadians(kneeDeg);
            this.ankleAngle = Math.toRadians(ankleDeg);
        }
    }

    private static final List<Keyframe> KEYFRAMES = new ArrayList<>();
    static {
        KEYFRAMES.add(new Keyframe("Debout", 0.0, 0, 0, 0));
        KEYFRAMES.add(new Keyframe("Début Descente", 1.0, 0, 0, 0));
        KEYFRAMES.add(new Keyframe("Plein Sujood", 3.0, -28, 110, -40));
        KEYFRAMES.add(new Keyframe("Maintien Sujood", 5.0, -28, 110, -40));
        KEYFRAMES.add(new Keyframe("Retour Debout", 7.0, 0, 0, 0));
        KEYFRAMES.add(new Keyframe("Fin du Cycle", 8.0, 0, 0, 0));
    }

    private static final double T_CYCLE_END = KEYFRAMES.get(KEYFRAMES.size() - 1).time;

    private String currentKeyframeName = "NONE";

    // Gains PD
    private double kpHip   = 50.0;
    private double kdHip   = 20.0;
    private double kpKnee  = 45.0;
    private double kdKnee  = 15.0;
    private double kpAnkle = 45.0;
    private double kdAnkle = 5.0;


    private double lastHipAngle = 0.0;
    private double lastKneeAngle = 0.0;
    private double lastAnkleAngle = 0.0;


    private double lastHipTorque = 0.0;
    private double lastKneeTorque = 0.0;
    private double lastAnkleTorque = 0.0;

    @Override
    public void reset() {
        this.lastAnkleTorque = 0.0;
        this.currentKeyframeName = "NONE";
    }

    @Override
    public MotorCommands computeCommands(SimulationState state, double time) {
        double dt = state.getDt();
        double t = time % T_CYCLE_END;

        double[] targets = computeInterpolatedTargets(t);
        double hipTarget   = targets[0];
        double kneeTarget  = targets[1];
        double ankleTarget = targets[2];

        Joint knee  = state.getHumanModel().getKneeJoint();
        Joint ankle = state.getHumanModel().getAnkleJoint();

        double currentHipAngle   = state.getHumanModel().getThigh().getAngle();
        double currentKneeAngle  = knee.getAngle();
        double currentAnkleAngle = ankle.getAngle();


        double hipError   = normalizeAngle(hipTarget - currentHipAngle);
        double kneeError  = normalizeAngle(kneeTarget - currentKneeAngle);
        double ankleError = normalizeAngle(ankleTarget - currentAnkleAngle);


        double estimatedHipOmega   = (currentHipAngle - this.lastHipAngle) / dt;
        double estimatedKneeOmega  = (currentKneeAngle - this.lastKneeAngle) / dt;
        double estimatedAnkleOmega = (currentAnkleAngle - this.lastAnkleAngle) / dt;

        this.lastHipAngle   = currentHipAngle;
        this.lastKneeAngle  = currentKneeAngle;
        this.lastAnkleAngle = currentAnkleAngle;


        double rawHipTorque   = kpHip * hipError + kdHip * (0.0 - estimatedHipOmega);
        double rawKneeTorque  = kpKnee * kneeError + kdKnee * (0.0 - estimatedKneeOmega);
        double rawAnkleTorque = kpAnkle * ankleError + kdAnkle * (0.0 - estimatedAnkleOmega);


        double smoothingFactor = 0.15;
        double hipTorque   = lastHipTorque + smoothingFactor * (rawHipTorque - lastHipTorque);
        double kneeTorque  = lastKneeTorque + smoothingFactor * (rawKneeTorque - lastKneeTorque);
        double ankleTorque = lastAnkleTorque + smoothingFactor * (rawAnkleTorque - lastAnkleTorque);

        this.lastHipTorque   = hipTorque;
        this.lastKneeTorque  = kneeTorque;
        this.lastAnkleTorque = ankleTorque;


        double MAX_TORQUE = 60.0;
        double MAX_ANKLE_TORQUE = 20.0;

        hipTorque   = Math.max(-MAX_TORQUE, Math.min(MAX_TORQUE, hipTorque));
        kneeTorque  = Math.max(-MAX_TORQUE, Math.min(MAX_TORQUE, kneeTorque));
        ankleTorque = Math.max(-MAX_ANKLE_TORQUE, Math.min(MAX_ANKLE_TORQUE, ankleTorque));

        return new MotorCommands(hipTorque, kneeTorque, ankleTorque);
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI)  angle -= 2.0 * Math.PI;
        while (angle < -Math.PI) angle += 2.0 * Math.PI;
        return angle;
    }


    private double[] computeInterpolatedTargets(double t) {
        Keyframe before = KEYFRAMES.get(0);
        Keyframe after = KEYFRAMES.get(KEYFRAMES.size() - 1);

        // Recherche des Keyframes qui encadrent le temps 't'
        for (int i = 0; i < KEYFRAMES.size() - 1; i++) {
            Keyframe k1 = KEYFRAMES.get(i);
            Keyframe k2 = KEYFRAMES.get(i + 1);
            if (t >= k1.time && t <= k2.time) {
                before = k1;
                after = k2;
                break;
            }
        }

        if (!before.name.equals(after.name)) {
            this.currentKeyframeName = before.name + " -> " + after.name;
        } else {
            this.currentKeyframeName = before.name;
        }


        double duration = after.time - before.time;
        if (duration <= 0) {
            return new double[]{before.hipAngle, before.kneeAngle, before.ankleAngle};
        }


        double alpha = smoothStep((t - before.time) / duration);


        double hip = before.hipAngle + alpha * (after.hipAngle - before.hipAngle);
        double knee = before.kneeAngle + alpha * (after.kneeAngle - before.kneeAngle);
        double ankle = before.ankleAngle + alpha * (after.ankleAngle - before.ankleAngle);

        return new double[]{hip, knee, ankle};
    }

    private double smoothStep(double t) {
        t = Math.max(0, Math.min(1, t));
        return 0.5 * (1.0 - Math.cos(Math.PI * t));
    }

    @Override
    public String getName() {
        return "Scripted Prostration Controller";
    }

    @Override
    public String getCurrentKeyframeName() {
        return this.currentKeyframeName;
    }

    public void setHipGains(double kp, double kd) { this.kpHip = kp; this.kdHip = kd; }
    public void setKneeGains(double kp, double kd) { this.kpKnee = kp; this.kdKnee = kd; }
    public void setAnkleGains(double kp, double kd) { this.kpAnkle = kp; this.kdAnkle = kd; }
}