package simulation.vision;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Owns one Python process. Blocking camera, model and pipe work never runs on JavaFX. */
public final class PoseInputService implements PoseSource {
    public enum State { OFF, STARTING, CAMERA, LOADING, TRACKING, LOST, ERROR }
    public record Status(State state, String message) { }
    public record Preview(PoseFrame pose, byte[] jpeg, long receivedNanos, double latencyMs, double inferenceMs) { }
    public record Metrics(double fps, double invalidPercent, long frames) { }
    private final ExecutorService commands = Executors.newSingleThreadExecutor(r -> daemon(r, "nova-camera-control"));
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Preview> preview = new AtomicReference<>();
    private final PoseMapper mapper = new PoseMapper();
    private volatile Status status = new Status(State.OFF, "Camera off");
    private volatile Metrics metrics = new Metrics(0, 0, 0);
    private volatile Consumer<PoseFrame> onFrame = f -> { };
    private volatile boolean closed;
    private volatile int cameraIndex;
    // Accessed only by the command executor.
    private volatile Process process;
    private BufferedWriter input;

    public void setCameraIndex(int index) {
        if (index < 0 || index > 20) throw new IllegalArgumentException("Camera index must be 0–20");
        cameraIndex = index;
    }
    public PoseMapper mapper() { return mapper; }
    public Status status() {
        Preview p = preview.get();
        Status current = status;
        if (p != null && (current.state == State.TRACKING || current.state == State.CAMERA || current.state == State.LOST)
                && System.nanoTime() - p.receivedNanos > 500_000_000L)
            return new Status(State.LOST, "Camera stream stalled · restart the camera");
        return current;
    }
    public Metrics metrics() { return metrics; }
    public Optional<Preview> preview() { return Optional.ofNullable(preview.get()); }
    public void onFrame(Consumer<PoseFrame> listener) { onFrame = Objects.requireNonNull(listener); }
    @Override public Optional<PoseFrame> latest() { return preview().map(Preview::pose); }
    @Override public boolean isHealthy() {
        Preview p = preview.get();
        return p != null && p.pose.trackingValid() && status().state == State.TRACKING
                && System.nanoTime() - p.receivedNanos < 500_000_000L;
    }

    @Override public synchronized void start() {
        if (closed) return;
        long token = generation.incrementAndGet();
        preview.set(null);
        mapper.reset();
        status = new Status(State.STARTING, "Opening device camera…");
        int device = cameraIndex;
        commands.execute(() -> {
            terminateProcess();
            if (generation.get() != token) return;
            try {
                Path root = Path.of(System.getProperty("nova.vision.dir", "vision")).toAbsolutePath();
                Path script = root.resolve("pose_service.py");
                if (!Files.isRegularFile(script)) throw new IOException("Camera worker missing. Launch from the repository root or set -Dnova.vision.dir.");
                String python = System.getProperty("nova.vision.python");
                if (python == null || python.isBlank()) {
                    boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
                    Path venv = root.getParent().resolve(windows ? ".venv/Scripts/python.exe" : ".venv/bin/python");
                    python = Files.isRegularFile(venv) ? venv.toString() : windows ? "python" : "python3";
                }
                Process child = new ProcessBuilder(python, "-u", script.toString(), "--camera", String.valueOf(device)).start();
                process = child;
                if (generation.get() != token) { child.destroyForcibly(); return; }
                input = new BufferedWriter(new OutputStreamWriter(child.getOutputStream(), StandardCharsets.UTF_8));
                // Native logs must not enter the protocol or block the child on a full stderr pipe.
                daemon(() -> drainErrors(child), "nova-camera-stderr").start();
                daemon(() -> readFrames(child, token), "nova-camera-reader").start();
                // Recover from missing permissions, hung drivers, or model initialization.
                daemon(() -> {
                    try { Thread.sleep(15_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    if (generation.get() == token && preview.get() == null && child.isAlive()) {
                        publishStatus(token, new Status(State.ERROR, "Camera startup timed out. Check OS permission and camera index."));
                        child.destroyForcibly();
                    }
                }, "nova-camera-watchdog").start();
            } catch (IOException | RuntimeException e) {
                publishStatus(token, new Status(State.ERROR,
                        "Camera unavailable: " + e.getMessage() + " See vision/README.md for setup."));
                terminateProcess();
            }
        });
    }

    public synchronized void enablePose(boolean enabled) {
        if (closed) return;
        long token = generation.get();
        mapper.reset();
        status = new Status(enabled ? State.LOADING : State.CAMERA,
                enabled ? "Loading MediaPipe…" : "Camera preview · MediaPipe off");
        commands.execute(() -> {
            if (generation.get() != token || input == null) return;
            try {
                input.write("{\"type\":\"pose\",\"enabled\":" + enabled + "}\n");
                input.flush();
            } catch (IOException e) {
                publishStatus(token, new Status(State.ERROR, "Camera worker stopped. Restart the camera."));
            }
        });
    }

    private void readFrames(Process child, long token) {
        long previousId = -1, previousTime = 0, count = 0, invalid = 0, poseCount = 0;
        long windowStart = System.nanoTime(), windowCount = 0;
        double fps = 0;
        String poseError = null;
        try (Reader reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = PoseProtocol.readLine(reader)) != null && generation.get() == token) {
                PoseProtocol.Packet packet = PoseProtocol.decode(line);
                if ("error".equals(packet.type())) throw new IOException(packet.message());
                if ("status".equals(packet.type())) {
                    if ("pose_error".equals(packet.state())) {
                        poseError = "MediaPipe unavailable: " + packet.message();
                        publishStatus(token, new Status(State.CAMERA, poseError));
                    } else {
                        poseError = null;
                        publishStatus(token, new Status("loading".equals(packet.state()) ? State.LOADING : State.CAMERA, packet.message()));
                    }
                    continue;
                }
                if (packet.frameId() <= previousId || packet.timestampMs() < previousTime) continue;
                long received = System.nanoTime();
                double latency = Math.max(0, System.currentTimeMillis() - packet.timestampMs());
                if (latency > 2000) continue;
                previousId = packet.frameId();
                previousTime = packet.timestampMs();
                byte[] jpeg = Base64.getDecoder().decode(packet.jpeg());
                if (jpeg.length < 4 || (jpeg[0] & 255) != 255 || (jpeg[1] & 255) != 216)
                    throw new IOException("Invalid camera image");
                // Publish atomically with respect to start/stop, so a closing worker
                // cannot overwrite a newer camera session or repopulate a stopped one.
                synchronized (this) {
                    if (generation.get() != token) break;
                    PoseFrame pose = mapper.map(packet.timestampMs(), packet.frameId(), packet.width(), packet.height(),
                            packet.poseEnabled() ? packet.landmarks() : List.of(), packet.worldLandmarks());
                    preview.set(new Preview(pose, jpeg, received, latency, packet.inferenceMs()));
                    count++; windowCount++;
                    if (packet.poseEnabled()) { poseCount++; if (!pose.trackingValid()) invalid++; }
                    if (received - windowStart >= 1_000_000_000L) {
                        fps = windowCount * 1e9 / (received - windowStart);
                        windowStart = received; windowCount = 0;
                    }
                    metrics = new Metrics(fps, poseCount == 0 ? 0 : invalid * 100.0 / poseCount, count);
                    if (packet.poseEnabled()) {
                        poseError = null;
                        status = new Status(pose.trackingValid() ? State.TRACKING : State.LOST,
                                pose.trackingValid() ? "Live tracking" : "Tracking lost · keep your full body in view");
                        onFrame.accept(pose);
                    } else if (status.state != State.LOADING) {
                        status = new Status(State.CAMERA, poseError == null ? "Camera preview · MediaPipe off" : poseError);
                    }
                }
            }
            synchronized (this) {
                if (generation.get() == token && status.state != State.ERROR)
                    status = new Status(State.ERROR, "Camera worker disconnected. Restart the camera.");
            }
        } catch (IOException | RuntimeException e) {
            publishStatus(token, new Status(State.ERROR, "Camera stopped: " + e.getMessage()));
        } finally {
            child.destroy();
        }
    }

    private synchronized void publishStatus(long token, Status next) {
        if (generation.get() == token) status = next;
    }

    private static void drainErrors(Process child) {
        try (InputStream errors = child.getErrorStream()) { errors.transferTo(OutputStream.nullOutputStream()); }
        catch (IOException ignored) { /* Process is closing. */ }
    }

    @Override public synchronized void stop() {
        if (closed) return;
        generation.incrementAndGet();
        preview.set(null);
        metrics = new Metrics(0, 0, 0);
        status = new Status(State.OFF, "Camera off");
        commands.execute(this::terminateProcess);
    }
    private void terminateProcess() {
        if (process == null) return;
        process.destroy();
        try {
            if (!process.waitFor(400, TimeUnit.MILLISECONDS)) process.destroyForcibly().waitFor(400, TimeUnit.MILLISECONDS);
            if (input != null) input.close();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
        catch (IOException ignored) { /* Already closed. */ }
        process = null; input = null;
    }
    @Override public synchronized void close() {
        if (closed) return;
        stop();
        // Signal the child immediately; daemon executor cleanup may outlive the FX stage.
        Process child = process;
        if (child != null) child.destroy();
        closed = true;
        commands.shutdown();
    }
    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread;
    }
}
