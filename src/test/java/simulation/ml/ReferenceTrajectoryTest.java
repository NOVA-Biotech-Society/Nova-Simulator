package simulation.ml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceTrajectoryTest {

    @Test
    void interpolatesBetweenKeyframesSmoothly() {
        ReferenceTrajectory trajectory = new ReferenceTrajectory();

        JointTargets standing = trajectory.fromPhase(Keyframe.STANDING, 0.0);
        JointTargets descending = trajectory.fromPhase(Keyframe.DESCENDING, 0.5);
        // Progress 1 reaches the NEXT keyframe: descent ends at prostration.
        JointTargets prostration = trajectory.fromPhase(Keyframe.DESCENDING, 1.0);

        assertEquals(0.0, standing.hipAngle(), 1e-12);
        assertTrue(descending.hipAngle() > standing.hipAngle());
        assertTrue(descending.kneeAngle() > standing.kneeAngle());
        assertTrue(prostration.kneeAngle() > descending.kneeAngle());
    }

    @Test
    void eachPhaseEndsAtTheNextKeyframe() {
        ReferenceTrajectory trajectory = new ReferenceTrajectory();
        for (Keyframe phase : Keyframe.values()) {
            JointTargets expected = trajectory.poseFor(phase.next());
            JointTargets actual = trajectory.fromPhase(phase, 1.0);
            assertEquals(expected.hipAngle(), actual.hipAngle(), 1e-12);
            assertEquals(expected.kneeAngle(), actual.kneeAngle(), 1e-12);
            assertEquals(expected.ankleAngle(), actual.ankleAngle(), 1e-12);
        }
    }
}
