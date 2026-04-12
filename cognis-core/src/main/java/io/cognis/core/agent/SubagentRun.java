package io.cognis.core.agent;

import java.time.Instant;

/**
 * Immutable snapshot of a subagent run record.
 * Stored in {@link SubagentRegistry} and persisted to disk for observability.
 */
public record SubagentRun(
    String runId,
    String parentRunId,
    String task,
    String role,
    String model,
    String traceId,
    String spanId,
    SubagentStatus status,
    Instant createdAt,
    Instant startedAt,
    Instant endedAt,
    Instant lastHeartbeatAt,
    String resultSummary
) {
    /** Convenience constructor — no times or result yet, status defaults to CREATED. */
    public SubagentRun(
        String runId, String parentRunId,
        String task, String role, String model,
        String traceId, String spanId
    ) {
        this(runId, parentRunId, task, role, model, traceId, spanId,
            SubagentStatus.CREATED, Instant.now(), null, null, null, null);
    }

    public SubagentRun withStatus(SubagentStatus newStatus) {
        return new SubagentRun(runId, parentRunId, task, role, model, traceId, spanId,
            newStatus, createdAt, startedAt, endedAt, lastHeartbeatAt, resultSummary);
    }

    public SubagentRun withStarted(Instant at) {
        return new SubagentRun(runId, parentRunId, task, role, model, traceId, spanId,
            SubagentStatus.RUNNING, createdAt, at, endedAt, at, resultSummary);
    }

    public SubagentRun withDone(Instant at, String summary) {
        return new SubagentRun(runId, parentRunId, task, role, model, traceId, spanId,
            SubagentStatus.DONE, createdAt, startedAt, at, at, summary);
    }

    public SubagentRun withFailed(Instant at, String summary) {
        return new SubagentRun(runId, parentRunId, task, role, model, traceId, spanId,
            SubagentStatus.FAILED, createdAt, startedAt, at, at, summary);
    }
}
