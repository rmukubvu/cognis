package io.cognis.core.agent;

import java.time.Instant;
import java.util.List;

/**
 * Persistent named agent definition created via {@code agent tool action=create}.
 *
 * <p>Saved to disk by {@link AgentStore} so it survives process restarts.
 * A DynamicAgent is the template; each {@code action=chat} invocation creates a fresh
 * {@link io.cognis.core.agent.AgentOrchestrator} session from the stored systemPrompt.
 */
public record DynamicAgent(
    String name,
    String description,
    String systemPrompt,
    String model,
    List<String> allowedTools,
    int maxToolIterations,
    Instant createdAt
) {
}
