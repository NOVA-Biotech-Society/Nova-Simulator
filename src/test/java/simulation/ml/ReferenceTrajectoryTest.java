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
        JointTargets prostration = trajectory.fromPhase(Keyframe.PROSTRATION, 1.0);

        assertEquals(0.0, standing.hipAngle(), 1e-12);
        assertTrue(descending.hipAngle() > standing.hipAngle());
        assertTrue(descending.kneeAngle() > standing.kneeAngle());
        assertTrue(prostration.kneeAngle() > descending.kneeAngle());
    }
}

