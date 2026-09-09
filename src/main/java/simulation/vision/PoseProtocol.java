package simulation.vision;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

/** Versioned, bounded NDJSON. Unknown fields allow additive protocol evolution. */
public final class PoseProtocol {
    public static final int VERSION = 1;
    public static final int MAX_LINE_LENGTH = 2_000_000;
    public static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private PoseProtocol() { }

    public record Packet(int version, String type, String state, String message,
                         long timestampMs, long frameId, int width, int height,
                         boolean poseEnabled, double inferenceMs, String jpeg,
                         List<PoseFrame.Landmark> landmarks, List<PoseFrame.Landmark> worldLandmarks) { }

    public static Packet decode(String line) {
        if (line == null || line.length() > MAX_LINE_LENGTH)
            throw new IllegalArgumentException("Invalid camera message size");
        Packet packet = JSON.fromJson(line, Packet.class);
        if (packet == null || packet.version() != VERSION || packet.type() == null)
            throw new IllegalArgumentException("Unsupported camera protocol");
        if ("frame".equals(packet.type())) {
            if (packet.timestampMs() <= 0 || packet.frameId() < 0 || packet.width() < 1 || packet.width() > 1920
                    || packet.height() < 1 || packet.height() > 1080 || packet.jpeg() == null
                    || packet.jpeg().length() > 1_500_000 || !Double.isFinite(packet.inferenceMs()) || packet.inferenceMs() < 0)
                throw new IllegalArgumentException("Invalid camera frame");
            PoseFrame.validateLandmarks(packet.landmarks());
            PoseFrame.validateLandmarks(packet.worldLandmarks());
        } else if (!("status".equals(packet.type()) || "error".equals(packet.type()))) {
            throw new IllegalArgumentException("Unknown camera message");
        } else if (packet.message() == null || packet.message().isBlank() || packet.message().length() > 4096
                || ("status".equals(packet.type()) && !("camera".equals(packet.state())
                || "opening".equals(packet.state()) || "loading".equals(packet.state()) || "pose_error".equals(packet.state())))) {
            throw new IllegalArgumentException("Invalid camera status");
        }
        return packet;
    }

    /** Unlike BufferedReader.readLine(), rejects oversized input before allocating it all. */
    public static String readLine(Reader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') return line.toString();
            if (line.length() >= MAX_LINE_LENGTH) throw new IOException("Camera/session message exceeds size limit");
            if (c != '\r') line.append((char)c);
        }
        return line.isEmpty() ? null : line.toString();
    }
}
