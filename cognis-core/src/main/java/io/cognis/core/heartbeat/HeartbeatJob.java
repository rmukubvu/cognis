package io.cognis.core.heartbeat;

import io.cognis.core.tool.ToolContext;

/**
 * A cron-scheduled job contributed by a vertical plugin via
 * {@code CognisVertical#heartbeatJobs()}.
 *
 * <p>Jobs are fired by {@link HeartbeatScheduler} on dedicated daemon threads according
 * to their {@link #cronExpression()}. They run outside the LLM agent loop and can use
 * {@link ToolContext} services directly (e.g. write to a supply store, push to a message bus).
 *
 * <h2>Cron syntax</h2>
 * Standard 5-field POSIX cron evaluated in UTC:
 * <pre>
 *   "0 6 * * *"          every day at 06:00 UTC
 *   "0 * * * *"          top of every hour
 *   "0-59/30 * * * *"    every 30 minutes
 *   "0 9 * * 1-5"        weekdays at 09:00 UTC
 * </pre>
 */
public interface HeartbeatJob {

    /** Unique name — used for de-duplication, logging, and shutdown reporting. */
    String name();

    /**
     * Standard 5-field cron expression evaluated in UTC.
     * Supported field syntax: {@code *}, single integer, {@code *\/n} (step),
     * {@code a-b} (range), {@code a-b\/n} (ranged step).
     */
    String cronExpression();

    /**
     * Execute the job. Called each time the cron expression fires.
     *
     * @param context tool context giving access to workspace path and shared services
     */
    void run(ToolContext context);
}
