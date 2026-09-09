package simulation.vision;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded asynchronous recording. Incomplete files keep a .partial suffix. No images are saved. */
public final class PoseSessionRecorder {
    private final ArrayBlockingQueue<PoseSession.Sample> queue = new ArrayBlockingQueue<>(256);
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final CompletableFuture<Path> completion = new CompletableFuture<>();
    private final String sessionId = UUID.randomUUID().toString();
    private volatile String failure;
    private volatile long count;

    public PoseSessionRecorder(Path target) {
        // Non-daemon: on app close, finish flushing the explicitly requested recording.
        Thread writer = new Thread(() -> write(target), "nova-pose-recorder");
        writer.start();
    }
    public synchronized void record(PoseFrame pose, String label) {
        if (!accepting.get()) return;
        if (!queue.offer(new PoseSession.Sample(1, "pose", sessionId, label, System.currentTimeMillis(), pose))) {
            failure = "Recording storage is too slow; the partial session was preserved.";
            accepting.set(false);
        }
    }
    public boolean isRecording() { return accepting.get(); }
    public long count() { return count; }
    public String failure() { return failure; }
    public synchronized CompletableFuture<Path> stop() { accepting.set(false); return completion; }

    private void write(Path target) {
        Path temporary = null;
        try {
            Path absolute = target.toAbsolutePath();
            temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName() + ".", ".partial");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(PoseProtocol.JSON.toJson(new PoseSession.Header(1, "session", sessionId,
                        System.currentTimeMillis(), "radians", "NOVA R&D observation; no video retained")));
                writer.newLine();
                while (accepting.get() || !queue.isEmpty()) {
                    PoseSession.Sample sample = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (sample != null) {
                        writer.write(PoseProtocol.JSON.toJson(sample)); writer.newLine();
                        if (++count % 30 == 0) writer.flush();
                    }
                }
            }
            if (failure != null) throw new IOException(failure);
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
            completion.complete(absolute);
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            failure = e.getMessage() + (temporary == null ? "" : " Partial file: " + temporary);
            accepting.set(false);
            completion.completeExceptionally(new IOException(failure, e));
        }
    }
}
