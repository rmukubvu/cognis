package io.cognis.core.provider;

import java.util.Locale;

public final class ProviderRouter {
    private final ProviderRegistry registry;

    public ProviderRouter(ProviderRegistry registry) {
        this.registry = registry;
    }

    public LlmProvider resolve(String preferredProvider, String model) {
        if (preferredProvider != null && !preferredProvider.isBlank()) {
            return registry.find(preferredProvider)
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + preferredProvider));
        }

        String normalizedModel = model == null ? "" : model.toLowerCase(Locale.ROOT);
        if (normalizedModel.startsWith("bedrock/")) {
            return registry.find("bedrock")
                .orElseThrow(() -> new IllegalArgumentException("Provider bedrock is not registered"));
        }
        if (normalizedModel.contains("codex")) {
            return registry.find("openai_codex")
                .orElseThrow(() -> new IllegalArgumentException("Provider openai_codex is not registered"));
        }
        if (normalizedModel.contains("copilot") || normalizedModel.contains("github")) {
            return registry.find("github_copilot")
                .orElseThrow(() -> new IllegalArgumentException("Provider github_copilot is not registered"));
        }
        if (normalizedModel.contains("claude")) {
            return registry.find("anthropic")
                .orElseThrow(() -> new IllegalArgumentException("Provider anthropic is not registered"));
        }
        if (normalizedModel.contains("gpt") || normalizedModel.startsWith("openai/")) {
            return registry.find("openai")
                .orElseThrow(() -> new IllegalArgumentException("Provider openai is not registered"));
        }

        return registry.find("openrouter")
            .orElseThrow(() -> new IllegalArgumentException("Provider openrouter is not registered"));
    }
}
