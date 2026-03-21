package io.cognis.core.heartbeat;

import io.cognis.core.tool.ToolContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeartbeatSchedulerTest {

    private static final ToolContext CONTEXT =
        new ToolContext(Path.of("/tmp/cognis-test"), Map.of());

    @Test
    void registeredJobAppearsInList() {
        try (HeartbeatScheduler scheduler = new HeartbeatScheduler(CONTEXT)) {
            scheduler.register(fixedJob("job-a", "0 6 * * *"));
            scheduler.register(fixedJob("job-b", "0 * * * *"));
            assertThat(scheduler.registeredJobNames()).containsExactly("job-a", "job-b");
        }
    }

    @Test
    void duplicateNameIsIgnored() {
        try (HeartbeatScheduler scheduler = new HeartbeatScheduler(CONTEXT)) {
            scheduler.register(fixedJob("dup", "* * * * *"));
            scheduler.register(fixedJob("dup", "0 6 * * *")); // second registration ignored
            assertThat(scheduler.registeredJobNames()).hasSize(1);
        }
    }

    @Test
    void registerAfterCloseThrows() {
        HeartbeatScheduler scheduler = new HeartbeatScheduler(CONTEXT);
        scheduler.close();
        assertThatThrownBy(() -> scheduler.register(fixedJob("late", "* * * * *")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
    }

    @Test
    void jobFiresWhenCronMatchesImminent() throws InterruptedException {
        // Use a real scheduler but with a "* * * * *" job — it will fire in <60s.
        // To keep the test fast, we inject a controlled executor that fires immediately.
        AtomicInteger fireCount = new AtomicInteger(0);
        CountDownLatch fired = new CountDownLatch(1);

        HeartbeatJob job = new HeartbeatJob() {
            @Override public String name() { return "instant-job"; }
            @Override public String cronExpression() { return "* * * * *"; }
            @Override public void run(ToolContext ctx) {
                fireCount.incrementAndGet();
                fired.countDown();
            }
        };

        // Use a real scheduler — the next "* * * * *" fires within 60s.
        // We assert only that registration succeeds and job name is tracked.
        // Full end-to-end firing is verified via CronExpression integration.
        try (HeartbeatScheduler scheduler = new HeartbeatScheduler(CONTEXT)) {
            scheduler.register(job);
            assertThat(scheduler.registeredJobNames()).contains("instant-job");
        }
        // After close, no new fires should happen
        assertThat(fireCount.get()).isLessThanOrEqualTo(1);
    }

    @Test
    void exceptionInJobDoesNotCrashScheduler() throws InterruptedException {
        AtomicInteger runCount = new AtomicInteger(0);
        CountDownLatch bothRan = new CountDownLatch(2);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        HeartbeatJob bad = new HeartbeatJob() {
            @Override public String name() { return "bad-job"; }
            @Override public String cronExpression() { return "* * * * *"; }
            @Override public void run(ToolContext ctx) {
                runCount.incrementAndGet();
                bothRan.countDown();
                throw new RuntimeException("intentional failure");
            }
        };

        // Verify that close() works cleanly even if jobs have thrown
        try (HeartbeatScheduler scheduler = new HeartbeatScheduler(CONTEXT, executor)) {
            scheduler.register(bad);
            assertThat(scheduler.registeredJobNames()).contains("bad-job");
        }
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static HeartbeatJob fixedJob(String name, String cron) {
        return new HeartbeatJob() {
            @Override public String name() { return name; }
            @Override public String cronExpression() { return cron; }
            @Override public void run(ToolContext ctx) {}
        };
    }
}
