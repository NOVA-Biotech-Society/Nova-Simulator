package simulation.vision;

/** Image-free, replayable dataset schema. Each line is independently readable JSON. */
public final class PoseSession {
    private PoseSession() { }
    public record Header(int version, String type, String sessionId, long createdAtMs, String units, String purpose) {
        public Header {
            if (version != 1 || !"session".equals(type) || sessionId == null || sessionId.isBlank()
                    || createdAtMs <= 0 || !"radians".equals(units))
                throw new IllegalArgumentException("Unsupported pose session header");
        }
    }
    public record Sample(int version, String type, String sessionId, String keyframeLabel, long receivedAtMs, PoseFrame pose) {
        public Sample {
            if (version != 1 || !"pose".equals(type) || sessionId == null || keyframeLabel == null
                    || keyframeLabel.length() > 120 || receivedAtMs <= 0 || pose == null)
                throw new IllegalArgumentException("Invalid pose session sample");
        }
    }
}
