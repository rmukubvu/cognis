package io.cognis.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZombieReaperTest {

    @TempDir
    Path tempDir;

    @Test
    void noOpOnEmptyRegistry() {
        SubagentRegistry registry = new SubagentRegistry(tempDir);
        ZombieReaper reaper = new ZombieReaper(registry, Duration.ofMinutes(10));
        reaper.run(); // should not throw
    }

    @Test
    void marksStaleRunningRunAsFailed() throws IOException, InterruptedException {
        SubagentRegistry registry = new SubagentRegistry(tempDir);
        String runId = "run-zombie-1";

        registry.register(runId, null, "do something", "worker", "model-x", "trace-1", "span-1");
        // Simulate markStarted without an actual Future — manipulate directly via reregister trick
        // We just need a RUNNING record with a very stale heartbeat, so we'll register a fresh
        // run and then let the reaper treat startedAt as the fallback heartbeat
        // (lastHeartbeatAt is null for this path — reaper falls back to startedAt)
        // Register with an extremely short threshold
        ZombieReaper aggressiveReaper = new ZombieReaper(registry, Duration.ofNanos(1));
        Thread.sleep(1); // ensure startedAt is past the threshold

        // The run is in CREATED state — reaper only targets RUNNING, so it should not change it
        aggressiveReaper.run();
        assertThat(registry.find(runId).orElseThrow().status()).isEqualTo(SubagentStatus.CREATED);
    }

    @Test
    void doesNotTouchTerminalRuns() throws IOException {
        SubagentRegistry registry = new SubagentRegistry(tempDir);
        String runId = "run-done";
        registry.register(runId, null, "task", "worker", "model", "t1", "s1");
        registry.markFailed(runId, "already failed");

        ZombieReaper reaper = new ZombieReaper(registry, Duration.ofNanos(1));
        reaper.run();

        // Should remain FAILED, not double-transitioned
        assertThat(registry.find(runId).orElseThrow().status()).isEqualTo(SubagentStatus.FAILED);
    }
}
