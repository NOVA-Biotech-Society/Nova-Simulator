package simulation.ml;

/**
 * Converts discrete keyframes into continuous joint targets using smooth interpolation.
 */
public class ReferenceTrajectory {

    public JointTargets fromPhase(Keyframe phase, double progress) {
        Keyframe current = phase == null ? Keyframe.STANDING : phase;
        Keyframe next = current.next();
        double t = clamp01(progress);
        JointTargets start = poseFor(current);
        JointTargets end = poseFor(next);
        double blend = cosineBlend(t);
        return new JointTargets(
                lerp(start.hipAngle(), end.hipAngle(), blend),
                lerp(start.kneeAngle(), end.kneeAngle(), blend),
                lerp(start.ankleAngle(), end.ankleAngle(), blend)
        );
    }

    public JointTargets poseFor(Keyframe keyframe) {
        return switch (keyframe == null ? Keyframe.STANDING : keyframe) {
            case STANDING -> new JointTargets(
                    Math.toRadians(0.0),
                    Math.toRadians(0.0),
                    Math.toRadians(0.0)
            );
            case DESCENDING -> new JointTargets(
                    Math.toRadians(28.0),
                    Math.toRadians(38.0),
                    Math.toRadians(-6.0)
            );
            case PROSTRATION -> new JointTargets(
                    Math.toRadians(110.0),
                    Math.toRadians(140.0),
                    Math.toRadians(-25.0)
            );
            case ASCENDING -> new JointTargets(
                    Math.toRadians(36.0),
                    Math.toRadians(62.0),
                    Math.toRadians(-8.0)
            );
            case RETURN_STANDING -> new JointTargets(
                    Math.toRadians(8.0),
                    Math.toRadians(10.0),
                    Math.toRadians(-2.0)
            );
        };
    }

    private double cosineBlend(double progress) {
        double t = clamp01(progress);
        return (1.0 - Math.cos(Math.PI * t)) * 0.5;
    }

    private double lerp(double start, double end, double alpha) {
        return start + (end - start) * alpha;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

