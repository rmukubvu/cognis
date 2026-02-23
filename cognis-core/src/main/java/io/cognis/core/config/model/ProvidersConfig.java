package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProvidersConfig(
    ProviderConfig openrouter,
    ProviderConfig openai,
    ProviderConfig anthropic,
    @JsonAlias({"openai_codex", "openai-codex"}) ProviderConfig openaiCodex,
    @JsonAlias({"github_copilot", "github-copilot"}) ProviderConfig githubCopilot
) {

    public static ProvidersConfig defaults() {
        return new ProvidersConfig(
            ProviderConfig.defaults(),
            ProviderConfig.defaults(),
            ProviderConfig.defaults(),
            ProviderConfig.defaults(),
            ProviderConfig.defaults()
        );
    }
}
