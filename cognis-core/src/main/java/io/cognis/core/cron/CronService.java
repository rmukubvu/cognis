package io.cognis.core.cron;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class CronService {
    private final CronStore store;
    private final Clock clock;

    public CronService(CronStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public synchronized CronJob addEvery(String name, int everySeconds, String message) throws IOException {
        if (everySeconds <= 0) {
            throw new IllegalArgumentException("everySeconds must be > 0");
        }

        List<CronJob> jobs = new ArrayList<>(store.load());
        long now = now();
        CronJob job = new CronJob(
            UUID.randomUUID().toString(),
            name,
            message,
            everySeconds,
            false,
            true,
            now + everySeconds * 1000L,
            0
        );
        jobs.add(job);
        store.save(jobs);
        return job;
    }

    public synchronized CronJob addIn(String name, int inSeconds, String message) throws IOException {
        if (inSeconds <= 0) {
            throw new IllegalArgumentException("inSeconds must be > 0");
        }
        return addAt(name, now() + inSeconds * 1000L, message);
    }

    public synchronized CronJob addAt(String name, long runAtEpochMs, String message) throws IOException {
        List<CronJob> jobs = new ArrayList<>(store.load());
        long scheduledAt = Math.max(now() + 1000L, runAtEpochMs);
        CronJob job = new CronJob(
            UUID.randomUUID().toString(),
            name,
            message,
            0,
            true,
            true,
            scheduledAt,
            0
        );
        jobs.add(job);
        store.save(jobs);
        return job;
    }

    public synchronized List<CronJob> list() throws IOException {
        return List.copyOf(store.load());
    }

    public synchronized boolean remove(String id) throws IOException {
        List<CronJob> jobs = new ArrayList<>(store.load());
        boolean removed = jobs.removeIf(job -> job.id().equals(id));
        if (removed) {
            store.save(jobs);
        }
        return removed;
    }

    public synchronized int runDue(Consumer<CronJob> runner) throws IOException {
        long now = now();
        int count = 0;
        List<CronJob> updated = new ArrayList<>();

        for (CronJob job : store.load()) {
            if (job.enabled() && now >= job.nextRunAtEpochMs()) {
                runner.accept(job);
                count++;
                if (!job.deleteAfterRun()) {
                    updated.add(new CronJob(
                        job.id(),
                        job.name(),
                        job.message(),
                        job.everySeconds(),
                        false,
                        job.enabled(),
                        now + job.everySeconds() * 1000L,
                        now
                    ));
                }
            } else {
                updated.add(job);
            }
        }

        if (count > 0) {
            store.save(updated);
        }
        return count;
    }

    private long now() {
        return clock.millis();
    }
}
