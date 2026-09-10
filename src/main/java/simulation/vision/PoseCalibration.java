package simulation.vision;

/** Thirty consecutive, stable standing observations establish the neutral offsets. */
public final class PoseCalibration {
    public static final int REQUIRED_SAMPLES = 30;
    private final double[] offsets = new double[3];
    private final double[] sum = new double[3];
    private double[] previous;
    private int samples;
    private boolean collecting, calibrated;

    public void begin() { reset(); collecting = true; }
    public void reset() {
        java.util.Arrays.fill(offsets, 0);
        collecting = false;
        calibrated = false;
        reject();
    }
    public void reject() {
        samples = 0;
        previous = null;
        java.util.Arrays.fill(sum, 0);
    }
    public void accept(double[] angles) {
        if (!collecting) return;
        // Calibration is a standing reference, never a way to zero a bent knee.
        if (Math.abs(angles[0]) > Math.toRadians(20) || angles[1] > Math.toRadians(15)
                || Math.abs(angles[2]) > Math.toRadians(20)) { reject(); return; }
        if (previous != null) {
            for (int i = 0; i < 3; i++) {
                if (Math.abs(angles[i] - previous[i]) > Math.toRadians(3)) { reject(); break; }
            }
        }
        previous = angles.clone();
        for (int i = 0; i < 3; i++) sum[i] += angles[i];
        if (++samples == REQUIRED_SAMPLES) {
            for (int i = 0; i < 3; i++) offsets[i] = sum[i] / samples;
            collecting = false;
            calibrated = true;
        }
    }
    public double offset(int index) { return offsets[index]; }
    public boolean isCalibrated() { return calibrated; }
    public boolean isCollecting() { return collecting; }
    public int samples() { return samples; }
}
