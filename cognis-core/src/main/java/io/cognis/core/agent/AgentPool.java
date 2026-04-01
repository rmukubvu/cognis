package io.cognis.core.agent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Semaphore-backed wrapper around an {@link ExecutorService} that limits how many subagent
 * runs execute concurrently.
 * <p>
 * Without this cap, a burst of inbound field messages (e.g. 200 SMS at once) would create
 * 200 in-flight LLM HTTP requests simultaneously — a thundering herd against provider rate
 * limits. With the pool, additional spawn calls queue internally and proceed as earlier
 * runs finish, providing natural backpressure.
 * <p>
 * <strong>Backpressure behaviour:</strong> if the semaphore cannot be acquired within
 * {@value ACQUIRE_TIMEOUT_MS} ms, {@link #submit} returns a failed {@link Future} with an
 * {@link AgentPoolFullException}. The LLM caller sees a JSON error result and can retry
 * with {@code action=status} later. This avoids blocking the parent agent indefinitely.
 */
public final class AgentPool {
    private static final Logger LOG = LoggerFactory.getLogger(AgentPool.class);
    private static final long ACQUIRE_TIMEOUT_MS = 200;

    private final ExecutorService executor;
    private final Semaphore permits;
    private final int maxConcurrent;

    public AgentPool(ExecutorService executor, int maxConcurrent) {
        this.executor = executor;
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.permits = new Semaphore(this.maxConcurrent, true);
    }

    /**
     * Submit a callable to the pool.
     * <p>
     * Acquires a permit before submitting; releases it when the callable completes
     * (success or exception). If no permit is available within the timeout the callable
     * is not submitted and the returned future completes exceptionally with
     * {@link AgentPoolFullException}.
     */
    public <T> Future<T> submit(Callable<T> callable) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(ACQUIRE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return failedFuture(new AgentPoolFullException("Interrupted while waiting for pool permit"));
        }

        if (!acquired) {
            LOG.warn("AgentPool at capacity ({}/{}), rejecting spawn", maxConcurrent, maxConcurrent);
            return failedFuture(new AgentPoolFullException(
                "Agent pool is at capacity (" + maxConcurrent + " concurrent runs). Retry later."));
        }

        return executor.submit(() -> {
            try {
                return callable.call();
            } finally {
                permits.release();
            }
        });
    }

    /** Returns the configured maximum concurrent runs. */
    public int maxConcurrent() {
        return maxConcurrent;
    }

    /** Returns the number of available permits (i.e. how many more spawns can start immediately). */
    public int availablePermits() {
        return permits.availablePermits();
    }

    /** Shuts down the underlying executor. Call on application shutdown. */
    public void shutdown() {
        executor.shutdownNow();
    }

    private static <T> Future<T> failedFuture(Exception ex) {
        java.util.concurrent.CompletableFuture<T> f = new java.util.concurrent.CompletableFuture<>();
        f.completeExceptionally(ex);
        return f;
    }

    /** Thrown when the pool is at capacity and the acquire timeout elapses. */
    public static final class AgentPoolFullException extends RuntimeException {
        public AgentPoolFullException(String message) {
            super(message);
        }
    }
}
