package io.cognis.core.agent;

/**
 * Lifecycle state of a subagent run tracked by {@link SubagentRegistry}.
 */
public enum SubagentStatus {
    /** Run has been registered but not yet picked up by the pool. */
    CREATED,
    /** Run is actively executing in the agent pool. */
    RUNNING,
    /** Run completed successfully. */
    DONE,
    /** Run terminated with an error. */
    FAILED,
    /** Run was explicitly cancelled by the parent agent or ZombieReaper. */
    KILLED,
    /** Run was abandoned due to an await timeout. */
    TIMEOUT
}
