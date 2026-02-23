package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolsConfig(WebToolsConfig web) {

    public static ToolsConfig defaults() {
        return new ToolsConfig(WebToolsConfig.defaults());
    }
}
