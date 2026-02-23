package io.cognis.core.cron;

public record CronJob(
    String id,
    String name,
    String message,
    int everySeconds,
    boolean deleteAfterRun,
    boolean enabled,
    long nextRunAtEpochMs,
    long lastRunAtEpochMs
) {
}
