package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentsConfig(AgentDefaults defaults) {

    public static AgentsConfig defaultConfig() {
        return new AgentsConfig(AgentDefaults.defaults());
    }
}
