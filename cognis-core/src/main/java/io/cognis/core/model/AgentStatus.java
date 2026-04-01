package io.cognis.core.model;

/**
 * Typed outcome of an {@link AgentResult}.
 * <p>
 * Callers should check this instead of string-matching {@link AgentResult#content()}
 * to avoid sending raw timeout messages (e.g. "Stopped after max tool iterations")
 * to end users.
 */
public enum AgentStatus {
    /** LLM stopped producing tool calls and returned a normal response. */
    SUCCESS,

    /** The agent consumed all {@code maxToolIterations} without producing a final answer. */
    MAX_ITERATIONS,

    /** A tool execution threw an unrecoverable exception on the last iteration. */
    TOOL_ERROR
}
