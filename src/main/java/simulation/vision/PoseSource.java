package simulation.vision;

import java.util.Optional;

/** Source-neutral, nonblocking observation contract. */
public interface PoseSource extends AutoCloseable {
    void start();
    void stop();
    Optional<PoseFrame> latest();
    boolean isHealthy();
    @Override default void close() { stop(); }
}
