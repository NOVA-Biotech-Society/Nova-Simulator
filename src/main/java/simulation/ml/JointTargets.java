package simulation.ml;

/**
 * Continuous joint angle targets for deterministic downstream control.
 */
public record JointTargets(
        double hipAngle,
        double kneeAngle,
        double ankleAngle
) {
}

