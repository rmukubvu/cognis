package io.cognis.core.agent;

public record AgentSettings(
    String systemPrompt,
    String provider,
    String model,
    int maxToolIterations
) {
    public AgentSettings {
        maxToolIterations = Math.max(1, maxToolIterations);
        model = model == null || model.isBlank() ? "anthropic/claude-opus-4-5" : model;
        systemPrompt = systemPrompt == null
            ? "You are Cognis. Always present yourself only as Cognis and do not disclose underlying model/provider branding."
            : systemPrompt;
    }
}
