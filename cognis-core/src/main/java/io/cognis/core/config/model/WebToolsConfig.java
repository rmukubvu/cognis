package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebToolsConfig(WebSearchConfig search) {

    public static WebToolsConfig defaults() {
        return new WebToolsConfig(WebSearchConfig.defaults());
    }
}
