package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CognisConfig(
    AgentsConfig agents,
    ProvidersConfig providers,
    ToolsConfig tools,
    WhatsAppConfig whatsapp
) {

    public static CognisConfig defaults() {
        return new CognisConfig(
            AgentsConfig.defaultConfig(),
            ProvidersConfig.defaults(),
            ToolsConfig.defaults(),
            WhatsAppConfig.defaults()
        );
    }

    /** Safe accessor — never returns null. */
    public WhatsAppConfig whatsappOrDefaults() {
        return whatsapp != null ? whatsapp : WhatsAppConfig.defaults();
    }
}
