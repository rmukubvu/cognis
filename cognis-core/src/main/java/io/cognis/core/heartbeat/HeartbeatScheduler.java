package io.cognis.core.heartbeat;

import io.cognis.core.tool.ToolContext;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedules and fires {@link HeartbeatJob} instances contributed by vertical plugins.
 *
 * <h2>How it works</h2>
 * Each registered job gets its own single-shot timer computed via
 * {@link CronExpression#nextExecution(ZonedDateTime)}. After each firing the
 * scheduler immediately computes and arms the next occurrence — giving exact
 * wall-clock cron semantics in UTC without a polling loop.
 *
 * <h2>Lifecycle</h2>
 * Call {@link #register(HeartbeatJob)} for every job before the gateway starts.
 * Call {@link #close()} at shutdown to drain the thread pool.
 */
public final class HeartbeatScheduler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final ScheduledExecutorService executor;
    private final ToolContext context;
    private final List<String> registeredNames = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public HeartbeatScheduler(ToolContext context) {
        this.context = context;
        this.executor = Executors.newScheduledThreadPool(
            2,
            r -> {
                Thread t = new Thread(r, "cognis-heartbeat");
                t.setDaemon(true);
                return t;
            }
        );
    }

    // package-private for testing
    HeartbeatScheduler(ToolContext context, ScheduledExecutorService executor) {
        this.context = context;
        this.executor = executor;
    }

    /**
     * Register a heartbeat job. Must be called before the gateway starts.
     * Duplicate names (same {@link HeartbeatJob#name()}) are silently ignored.
     *
     * @throws IllegalStateException if the scheduler has already been closed
     */
    public void register(HeartbeatJob job) {
        if (closed.get()) {
            throw new IllegalStateException("HeartbeatScheduler is closed");
        }
        if (registeredNames.contains(job.name())) {
            LOG.warn("Heartbeat job '{}' is already registered — ignoring duplicate", job.name());
            return;
        }
        CronExpression expr = CronExpression.parse(job.cronExpression());
        registeredNames.add(job.name());
        armNext(job, expr);
        LOG.info("Registered heartbeat job '{}' with cron '{}'", job.name(), job.cronExpression());
    }

    /** Returns an unmodifiable snapshot of registered job names, for diagnostics. */
    public List<String> registeredJobNames() {
        return List.copyOf(registeredNames);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
            LOG.info("HeartbeatScheduler shut down ({} jobs were registered)", registeredNames.size());
        }
    }

    // ── Internal scheduling ──────────────────────────────────────────────────

    private void armNext(HeartbeatJob job, CronExpression expr) {
        if (closed.get()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime next;
        try {
            next = expr.nextExecution(now);
        } catch (IllegalStateException e) {
            LOG.error("Heartbeat job '{}': cannot compute next execution — job will not fire again", job.name(), e);
            return;
        }

        long delayMs = Math.max(0, next.toInstant().toEpochMilli() - now.toInstant().toEpochMilli());
        LOG.debug("Heartbeat job '{}' next execution at {} (in {}ms)", job.name(), next, delayMs);

        executor.schedule(
            () -> fireAndRearm(job, expr),
            delayMs,
            TimeUnit.MILLISECONDS
        );
    }

    private void fireAndRearm(HeartbeatJob job, CronExpression expr) {
        if (closed.get()) {
            return;
        }
        LOG.info("Firing heartbeat job '{}'", job.name());
        try {
            job.run(context);
        } catch (Exception e) {
            LOG.warn("Heartbeat job '{}' threw an exception", job.name(), e);
        } finally {
            armNext(job, expr);
        }
    }
}
