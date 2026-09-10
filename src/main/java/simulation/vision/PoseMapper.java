package simulation.vision;

import java.util.List;
import simulation.vision.PoseFrame.Landmark;

/** Sagittal mapping in pixel coordinates, so non-square images do not distort angles.
 * Hip uses absolute thigh tilt from the downward vertical, as HumanModel requires.
 * Positive X in NOVA is opposite the subject's facing direction (matching the
 * existing positive knee-flexion convention). Ankle neutral is a perpendicular foot.
 */
public final class PoseMapper {
    public enum Side { LEFT, RIGHT }
    public enum Facing { LEFT, RIGHT }
    private Side side = Side.LEFT;
    private Facing facing = Facing.RIGHT;
    private final PoseCalibration calibration = new PoseCalibration();
    private double[] filtered;
    private long lastTimestamp;

    public synchronized void configure(Side side, Facing facing) {
        this.side = java.util.Objects.requireNonNull(side);
        this.facing = java.util.Objects.requireNonNull(facing);
        reset();
    }
    public synchronized void reset() { filtered = null; lastTimestamp = 0; calibration.reset(); }
    public synchronized void calibrate() { filtered = null; calibration.begin(); }
    public synchronized String calibrationStatus() {
        return calibration.isCollecting() ? "Hold standing · " + calibration.samples() + "/30"
                : calibration.isCalibrated() ? "Standing calibrated" : "Calibration recommended";
    }

    public synchronized PoseFrame map(long timestamp, long id, int width, int height,
                                       List<Landmark> points, List<Landmark> world) {
        PoseFrame.validateLandmarks(points);
        PoseFrame.validateLandmarks(world);
        double[] raw = new double[3], confidence = new double[3], velocity = new double[3];
        boolean valid = points.size() == 33;
        if (valid) {
            int s = side == Side.LEFT ? 0 : 1;
            Landmark shoulder = points.get(11 + s), hip = points.get(23 + s), knee = points.get(25 + s);
            Landmark ankle = points.get(27 + s), heel = points.get(29 + s), toe = points.get(31 + s);
            confidence[0] = confidence(shoulder, hip, knee);
            confidence[1] = confidence(hip, knee, ankle);
            confidence[2] = confidence(knee, ankle, heel, toe);
            valid = Math.min(confidence[0], Math.min(confidence[1], confidence[2])) >= PoseFrame.MIN_CONFIDENCE;
            // Off-screen landmarks are not reliable sagittal observations.
            for (Landmark p : new Landmark[]{shoulder, hip, knee, ankle, heel, toe})
                valid &= p.x() >= 0 && p.x() <= 1 && p.y() >= 0 && p.y() <= 1;
            if (valid) {
                double[] thigh = vector(hip, knee, width, height);
                double[] kneeToHip = vector(knee, hip, width, height);
                double[] shank = vector(knee, ankle, width, height);
                double[] ankleToKnee = vector(ankle, knee, width, height);
                double[] foot = vector(heel, toe, width, height);
                raw[0] = Math.atan2((facing == Facing.RIGHT ? -1 : 1) * thigh[0], thigh[1]);
                raw[1] = Math.PI - angle(kneeToHip, shank);
                raw[2] = Math.PI / 2 - angle(ankleToKnee, foot);
                valid = length(thigh) > 2 && length(shank) > 2 && length(foot) > 2;
                for (double value : raw) valid &= Double.isFinite(value);
            }
        }
        double[] mapped = new double[3];
        if (valid) {
            boolean wasCalibrated = calibration.isCalibrated();
            calibration.accept(raw);
            if (wasCalibrated != calibration.isCalibrated()) filtered = null;
            double dt = (timestamp - lastTimestamp) / 1000.0;
            // A gap or lost tracking resets history: never differentiate across it.
            if (dt <= 0 || dt > 0.25) filtered = null;
            double alpha = filtered == null ? 1 : 1 - Math.exp(-dt / 0.07);
            double[] min = {-30, 0, -50}, max = {130, 140, 30};
            for (int i = 0; i < 3; i++) {
                double target = clamp(raw[i] - calibration.offset(i), Math.toRadians(min[i]), Math.toRadians(max[i]));
                mapped[i] = filtered == null ? target : filtered[i] + alpha * (target - filtered[i]);
                velocity[i] = filtered == null ? 0 : clamp((mapped[i] - filtered[i]) / dt, -12, 12);
            }
            filtered = mapped.clone();
            lastTimestamp = timestamp;
        } else {
            filtered = null;
            lastTimestamp = 0;
            calibration.reject();
            raw = new double[3];
        }
        return new PoseFrame(timestamp, id, width, height, mapped[0], mapped[1], mapped[2],
                velocity[0], velocity[1], velocity[2], raw[0], raw[1], raw[2],
                confidence[0], confidence[1], confidence[2], valid, calibration.isCalibrated(),
                side.name(), facing.name(), points, world);
    }

    private static double confidence(Landmark... points) {
        double result = 1;
        for (Landmark p : points) result = Math.min(result, p.confidence());
        return result;
    }
    private static double[] vector(Landmark a, Landmark b, int w, int h) {
        return new double[]{(b.x() - a.x()) * w, (b.y() - a.y()) * h};
    }
    private static double length(double[] v) { return Math.hypot(v[0], v[1]); }
    private static double angle(double[] a, double[] b) {
        double denominator = length(a) * length(b);
        return denominator < 1e-8 ? Double.NaN : Math.acos(clamp((a[0]*b[0] + a[1]*b[1]) / denominator, -1, 1));
    }
    private static double clamp(double n, double min, double max) { return Math.max(min, Math.min(max, n)); }
}
