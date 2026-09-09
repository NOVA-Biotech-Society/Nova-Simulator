package simulation.vision;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Streams an image-free session using recorded timing, with constant memory use. */
public final class ReplayPoseSource implements PoseSource {
    private final Path path;
    private final AtomicLong generation = new AtomicLong();
    private volatile PoseFrame latest;
    private volatile String status = "Replay ready", label = "Unlabelled";
    private volatile boolean running;
    private volatile long receivedNanos;
    private volatile Thread worker;
    public ReplayPoseSource(Path path) { this.path = path; }
    public String status() { return status; }
    public String label() { return label; }
    public boolean isRunning() { return running; }

    @Override public synchronized void start() {
        stop();
        long token = generation.incrementAndGet();
        running = true;
        status = "Loading replay…";
        worker = new Thread(() -> replay(token), "nova-pose-replay");
        worker.setDaemon(true); worker.start();
    }
    private void replay(long token) {
        try (Reader reader = new BufferedReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            String headerLine = PoseProtocol.readLine(reader);
            PoseSession.Header header = PoseProtocol.JSON.fromJson(headerLine, PoseSession.Header.class);
            if (header == null) throw new IOException("Empty pose session");
            long firstTimestamp = -1, previousTimestamp = -1, previousId = -1, started = System.nanoTime();
            String line;
            while (generation.get() == token && (line = PoseProtocol.readLine(reader)) != null) {
                PoseSession.Sample sample = PoseProtocol.JSON.fromJson(line, PoseSession.Sample.class);
                if (sample == null || !sample.sessionId().equals(header.sessionId())) throw new IOException("Session identifier mismatch");
                PoseFrame frame = sample.pose();
                if (frame.timestampMs() < previousTimestamp || frame.frameId() <= previousId)
                    throw new IOException("Pose session is not chronologically ordered");
                if (firstTimestamp < 0) firstTimestamp = frame.timestampMs();
                if (frame.timestampMs() - firstTimestamp > 24 * 60 * 60 * 1000L) throw new IOException("Session exceeds 24 hours");
                long due = started + (frame.timestampMs() - firstTimestamp) * 1_000_000L;
                while (generation.get() == token && System.nanoTime() < due)
                    Thread.sleep(Math.max(1, Math.min(25, (due - System.nanoTime()) / 1_000_000)));
                if (generation.get() != token) return;
                latest = frame;
                label = sample.keyframeLabel();
                receivedNanos = System.nanoTime();
                status = frame.trackingValid() ? "Replaying · " + label : "Replay · tracking lost in recording";
                previousTimestamp = frame.timestampMs(); previousId = frame.frameId();
            }
            if (generation.get() == token) status = latest == null ? "Replay contains no frames" : "Replay complete";
        } catch (IOException | RuntimeException e) {
            if (generation.get() == token) { latest = null; status = "Replay failed: " + e.getMessage(); }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (generation.get() == token) running = false;
        }
    }
    @Override public synchronized void stop() {
        generation.incrementAndGet(); running = false; latest = null;
        if (worker != null) worker.interrupt();
        status = "Replay stopped";
    }
    @Override public Optional<PoseFrame> latest() { return Optional.ofNullable(latest); }
    @Override public boolean isHealthy() {
        return running && latest != null && latest.trackingValid() && System.nanoTime() - receivedNanos < 500_000_000L;
    }
}
