package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CognisConfig(
    AgentsConfig agents,
    ProvidersConfig providers,
    ToolsConfig tools
) {

    public static CognisConfig defaults() {
        return new CognisConfig(
            AgentsConfig.defaultConfig(),
            ProvidersConfig.defaults(),
            ToolsConfig.defaults()
        );
    }
}
