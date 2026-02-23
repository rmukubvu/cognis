package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentDefaults(
    String workspace,
    String provider,
    String model,
    int maxTokens,
    double temperature,
    int maxToolIterations
) {

    public static AgentDefaults defaults() {
        return new AgentDefaults(
            "~/.cognis/workspace",
            "openrouter",
            "anthropic/claude-opus-4-5",
            8192,
            0.7,
            20
        );
    }
}
