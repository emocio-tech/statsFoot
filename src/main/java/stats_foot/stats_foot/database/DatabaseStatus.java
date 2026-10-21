package stats_foot.stats_foot.database;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Remembers whether the database start-up (schema script and data import) failed.
 *
 * <p>Thread-safe: written once at start-up and read by request threads afterwards.
 */
@Component
public class DatabaseStatus {

    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    /** Stores the start-up failure (replaces any previous one). */
    public void recordFailure(Throwable cause) {
        failure.set(cause);
    }

    /** Forgets any previous failure. */
    public void recordSuccess() {
        failure.set(null);
    }

    public boolean isFailed() {
        return failure.get() != null;
    }

    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure.get());
    }
}
