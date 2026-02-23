package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderConfig(
    @JsonAlias({"api_key"}) String apiKey,
    @JsonAlias({"api_base"}) String apiBase,
    @JsonAlias({"auth_method"}) String authMethod,
    @JsonAlias({"account_id"}) String accountId,
    @JsonAlias({"extra_headers"}) Map<String, String> extraHeaders
) {

    public static ProviderConfig defaults() {
        return new ProviderConfig("", null, "", "", Map.of());
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
