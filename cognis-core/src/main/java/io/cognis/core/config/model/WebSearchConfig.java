package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebSearchConfig(String apiKey, int maxResults) {

    public static WebSearchConfig defaults() {
        return new WebSearchConfig("", 5);
    }
}
