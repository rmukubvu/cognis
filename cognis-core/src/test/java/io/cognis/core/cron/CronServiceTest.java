package io.cognis.core.cron;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CronServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistAndRunDueJobs() throws Exception {
        FileCronStore store = new FileCronStore(tempDir.resolve("cron/jobs.json"));
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);
        CronService service = new CronService(store, clock);

        CronJob job = service.addEvery("heartbeat", 1, "ping");
        assertThat(service.list()).hasSize(1);

        // same fixed clock: not due yet
        List<String> fired = new ArrayList<>();
        int firstRun = service.runDue(j -> fired.add(j.message()));
        assertThat(firstRun).isZero();

        CronService advanced = new CronService(
            store,
            Clock.fixed(Instant.ofEpochMilli(job.nextRunAtEpochMs() + 1), ZoneOffset.UTC)
        );
        int secondRun = advanced.runDue(j -> fired.add(j.message()));
        assertThat(secondRun).isEqualTo(1);
        assertThat(fired).containsExactly("ping");
    }

    @Test
    void shouldDeleteOneShotJobsAfterRun() throws Exception {
        FileCronStore store = new FileCronStore(tempDir.resolve("cron/jobs.json"));
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);
        CronService service = new CronService(store, clock);

        CronJob oneShot = service.addIn("once", 1, "run once");
        CronService advanced = new CronService(
            store,
            Clock.fixed(Instant.ofEpochMilli(oneShot.nextRunAtEpochMs() + 1), ZoneOffset.UTC)
        );

        List<String> fired = new ArrayList<>();
        int ran = advanced.runDue(j -> fired.add(j.message()));

        assertThat(ran).isEqualTo(1);
        assertThat(fired).containsExactly("run once");
        assertThat(advanced.list()).isEmpty();
    }
}
