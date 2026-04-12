package io.cognis.core.agent;

import io.cognis.core.model.AgentResult;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * In-memory registry of subagent runs, keyed by {@code runId}.
 *
 * <p>Thread-safe. All mutating methods synchronise on {@code this} to ensure
 * consistent snapshot transitions. Reads (find, list) use the concurrent map directly
 * and may see slightly stale data for concurrent writers — acceptable since all callers
 * tolerate eventual consistency for status queries.
 */
public final class SubagentRegistry {

    private final ConcurrentHashMap<String, SubagentRun>       runs    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SubagentRunHandle> handles = new ConcurrentHashMap<>();

    /** No-arg constructor — fully in-memory registry. */
    public SubagentRegistry() {}

    /**
     * Path constructor — accepts a workspace path for API compatibility with callers
     * that previously expected disk-backed persistence. The path is currently unused;
     * all state is in-memory. A file-backed store can be added later if needed.
     */
    public SubagentRegistry(java.nio.file.Path workspacePath) {
        // path accepted but not used — in-memory only
    }

    // -------------------------------------------------------------------------
    // Lifecycle transitions
    // -------------------------------------------------------------------------

    /**
     * Register a brand-new run at CREATED status.
     */
    public synchronized void register(
        String runId, String parentRunId,
        String task, String role, String model,
        String traceId, String spanId
    ) throws IOException {
        SubagentRun run = new SubagentRun(runId, parentRunId, task, role, model, traceId, spanId);
        runs.put(runId, run);
    }

    /**
     * Re-register an existing run (used by steer to replace the task with a new one).
     */
    public synchronized void reregister(
        String runId, String parentRunId,
        String task, String role, String model,
        String traceId, String spanId
    ) throws IOException {
        register(runId, parentRunId, task, role, model, traceId, spanId);
    }

    /**
     * Attach the resolved {@link Future} to a registered run.
     * Called by {@link io.cognis.core.tool.impl.AgentTool} immediately after pool submission.
     */
    public void registerHandle(String runId, Future<AgentResult> future) {
        SubagentRun snapshot = runs.getOrDefault(runId, null);
        SubagentRunHandle handle = new SubagentRunHandle(future, snapshot);
        handles.put(runId, handle);
    }

    /**
     * Transition to RUNNING and record the executing thread for interrupt support.
     * Called by the pool task at the start of execution.
     */
    public synchronized void markStarted(String runId, Thread executingThread) {
        SubagentRun run = runs.get(runId);
        if (run != null) {
            SubagentRun updated = run.withStarted(Instant.now());
            runs.put(runId, updated);
            SubagentRunHandle handle = handles.get(runId);
            if (handle != null) {
                handle.thread   = executingThread;
                handle.snapshot = updated;
            }
        }
    }

    /** Transition to DONE. */
    public synchronized void markDone(String runId, String resultSummary) {
        update(runId, existing -> existing.withDone(Instant.now(), resultSummary));
    }

    /** Transition to FAILED. */
    public synchronized void markFailed(String runId, String errorMessage) throws IOException {
        update(runId, existing -> existing.withFailed(Instant.now(), errorMessage));
    }

    /**
     * Update the heartbeat timestamp for a running subagent.
     * Called by {@link AgentOrchestrator} after each tool iteration so the
     * {@link ZombieReaper} can distinguish healthy long-running agents from stalled ones.
     */
    public synchronized void updateHeartbeat(String runId) {
        update(runId, existing -> new SubagentRun(
            existing.runId(), existing.parentRunId(), existing.task(),
            existing.role(), existing.model(), existing.traceId(), existing.spanId(),
            existing.status(), existing.createdAt(), existing.startedAt(),
            existing.endedAt(), Instant.now(), existing.resultSummary()
        ));
    }

    /** Transition to KILLED and cancel the future. */
    public synchronized void markKilled(String runId) throws IOException {
        SubagentRunHandle handle = handles.get(runId);
        if (handle != null && !handle.future.isDone()) {
            handle.future.cancel(true);
            if (handle.thread != null) handle.thread.interrupt();
        }
        update(runId, existing -> existing.withStatus(SubagentStatus.KILLED));
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public Optional<SubagentRun> find(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    public SubagentRunHandle getHandle(String runId) {
        return handles.get(runId);
    }

    public List<SubagentRun> listAll() throws IOException {
        return new ArrayList<>(runs.values());
    }

    public List<SubagentRun> listByParent(String parentRunId) throws IOException {
        return runs.values().stream()
            .filter(r -> parentRunId.equals(r.parentRunId()))
            .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface RunTransform {
        SubagentRun apply(SubagentRun existing);
    }

    private void update(String runId, RunTransform transform) {
        runs.computeIfPresent(runId, (k, existing) -> {
            SubagentRun updated = transform.apply(existing);
            SubagentRunHandle handle = handles.get(runId);
            if (handle != null) handle.snapshot = updated;
            return updated;
        });
    }
}
