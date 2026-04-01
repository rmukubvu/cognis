package io.cognis.core.agent;

import java.util.UUID;

/**
 * Immutable trace/span context propagated through every spawn chain.
 * <p>
 * A single {@code traceId} is shared across an entire request tree (root + all descendants).
 * Each agent run gets its own {@code spanId}; the {@code parentSpanId} links it to the caller.
 * Every {@link io.cognis.core.observability.ObservabilityService} event emits these IDs so the
 * full call tree can be reconstructed post-hoc from the audit trail.
 * <p>
 * Usage:
 * <pre>{@code
 * // At the root orchestrator entry point (root already present in toolServices if provided):
 * TraceContext root = TraceContext.root();
 *
 * // When spawning a child agent:
 * TraceContext child = root.child();
 * }</pre>
 */
public record TraceContext(String traceId, String spanId, String parentSpanId) {

    /** Creates a fresh root context — new traceId and spanId, no parent. */
    public static TraceContext root() {
        return new TraceContext(uuid(), uuid(), null);
    }

    /** Creates a child span that inherits this trace's {@code traceId} and uses this span as parent. */
    public TraceContext child() {
        return new TraceContext(traceId, uuid(), spanId);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
