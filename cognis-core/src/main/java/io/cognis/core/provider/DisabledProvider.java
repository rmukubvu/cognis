package io.cognis.core.provider;

import io.cognis.core.model.ChatMessage;
import java.util.List;
import java.util.Map;

/**
 * Provider placeholder used when a provider is not configured.
 * Returns a deterministic error so fallback chains can skip it.
 */
public final class DisabledProvider implements LlmProvider {
    private final String name;
    private final String reason;

    public DisabledProvider(String name, String reason) {
        this.name = name;
        this.reason = reason == null || reason.isBlank() ? "provider is disabled" : reason;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
        return new LlmResponse(
            "Error calling LLM: provider " + name + " is not configured (" + reason + ")",
            List.of(),
            Map.of("provider", name, "disabled", true)
        );
    }
}
