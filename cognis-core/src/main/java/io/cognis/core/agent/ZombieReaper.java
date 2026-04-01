package io.cognis.core.agent;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background task that detects and terminates "zombie" subagent runs — runs whose
 * {@link SubagentStatus} is {@code RUNNING} but whose {@code lastHeartbeatAt} timestamp
 * has not been updated within the configured {@code staleness} threshold.
 * <p>
 * A run becomes a zombie when its virtual thread is blocked indefinitely on an LLM HTTP call
 * that never returns (provider outage, network drop). Without this reaper the run stays
 * {@code RUNNING} forever, consuming a slot in the {@link AgentPool}.
 * <p>
 * The reaper is scheduled in {@code CognisApplication} via:
 * <pre>{@code
 * scheduler.scheduleAtFixedRate(
 *     new ZombieReaper(subagentRegistry, Duration.ofMinutes(10)), 1, 1, TimeUnit.MINUTES);
 * }</pre>
 * <p>
 * <strong>Safe threshold:</strong> set {@code staleness} to at least
 * {@code maxToolIterations * avgToolDurationSeconds + margin}. The default of 10 minutes
 * accommodates up to ~20 tool iterations averaging 25s each.
 */
public final class ZombieReaper implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ZombieReaper.class);

    private final SubagentRegistry registry;
    private final Duration staleness;

    public ZombieReaper(SubagentRegistry registry, Duration staleness) {
        this.registry = registry;
        this.staleness = staleness;
    }

    @Override
    public void run() {
        List<SubagentRun> runs;
        try {
            runs = registry.listAll();
        } catch (IOException e) {
            LOG.warn("ZombieReaper: failed to list runs: {}", e.getMessage());
            return;
        }

        Instant cutoff = Instant.now().minus(staleness);
        int reaped = 0;

        for (SubagentRun run : runs) {
            if (run.status() != SubagentStatus.RUNNING) continue;

            Instant heartbeat = run.lastHeartbeatAt() != null
                ? run.lastHeartbeatAt()
                : run.startedAt();    // fall back to startedAt for runs without heartbeat

            if (heartbeat == null || heartbeat.isBefore(cutoff)) {
                LOG.warn("ZombieReaper: marking run {} as FAILED (last heartbeat: {}, threshold: {})",
                    run.runId(), heartbeat, staleness);
                try {
                    registry.markFailed(run.runId(), "zombie: no heartbeat for " + staleness);
                    reaped++;
                } catch (IOException e) {
                    LOG.warn("ZombieReaper: failed to mark run {} as failed: {}", run.runId(), e.getMessage());
                }
            }
        }

        if (reaped > 0) {
            LOG.info("ZombieReaper: reaped {} zombie run(s)", reaped);
        }
    }
}
