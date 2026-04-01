package io.cognis.core.agent;

import java.util.List;

/**
 * A single unit of work within a {@link TaskQueue}.
 *
 * @param id           unique identifier within the graph (referenced in {@code dependsOn})
 * @param prompt       what the spawned agent should do
 * @param role         role label passed to the subagent (e.g. {@code "researcher"})
 * @param dependsOn    IDs of tasks that must complete before this one starts; empty = no deps
 * @param toolAllowlist tools this agent may use; {@code null} inherits the parent's allowlist
 * @param model        LLM model override; {@code null} uses the default
 */
public record Task(
    String id,
    String prompt,
    String role,
    List<String> dependsOn,
    List<String> toolAllowlist,
    String model
) {
    /** Convenience constructor for tasks with no dependencies or overrides. */
    public Task(String id, String prompt, String role) {
        this(id, prompt, role, List.of(), null, null);
    }

    public Task {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
