package io.cognis.core.agent;

import io.cognis.core.model.AgentResult;
import java.util.concurrent.Future;

/**
 * Live handle to a running subagent — pairs the {@link Future} from the pool
 * with the owning thread (for interrupt/steer support) and the in-memory snapshot.
 */
public final class SubagentRunHandle {

    /** Future resolving to the agent's final {@link AgentResult}. */
    public final Future<AgentResult> future;

    /**
     * Virtual thread executing the run. Set once the run starts; {@code null} before that.
     * Used by {@code steer} and {@code kill} to interrupt the thread if needed.
     */
    public volatile Thread thread;

    /** Snapshot at the time the handle was created (or last re-registered via steer). */
    public volatile SubagentRun snapshot;

    public SubagentRunHandle(Future<AgentResult> future, SubagentRun snapshot) {
        this.future   = future;
        this.snapshot = snapshot;
    }
}
