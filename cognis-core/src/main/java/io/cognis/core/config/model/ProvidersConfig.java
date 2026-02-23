package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProvidersConfig(
    ProviderConfig openrouter,
    ProviderConfig openai,
    ProviderConfig anthropic,
    @JsonProperty("openai_codex")
    @JsonAlias({"openai_codex", "openai-codex"}) ProviderConfig openaiCodex,
    @JsonProperty("github_copilot")
    @JsonAlias({"github_copilot", "github-copilot"}) ProviderConfig githubCopilot,
    @JsonProperty("bedrock")
    @JsonAlias({"bedrock"}) ProviderConfig bedrock,
    @JsonProperty("bedrock_openai")
    @JsonAlias({"bedrock_openai", "bedrock-openai"}) ProviderConfig bedrockOpenai
) {

    public static ProvidersConfig defaults() {
        return new ProvidersConfig(
            ProviderConfig.defaults(),
            ProviderConfig.defaults(),
            ProviderConfig.defaults(),
            ProviderConfig.defaults(),
            ProviderConfig.defaults(),
            ProviderConfig.defaults(),
            ProviderConfig.defaults()
        );
    }
}
