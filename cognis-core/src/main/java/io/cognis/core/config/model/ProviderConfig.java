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
    @JsonAlias({"extra_headers"}) Map<String, String> extraHeaders,
    @JsonAlias({"region"}) String region,
    @JsonAlias({"access_key_id"}) String accessKeyId,
    @JsonAlias({"secret_access_key"}) String secretAccessKey,
    @JsonAlias({"session_token"}) String sessionToken,
    @JsonAlias({"profile"}) String profile
) {

    public static ProviderConfig defaults() {
        return new ProviderConfig("", null, "", "", Map.of(), "", "", "", "", "");
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean configuredForBedrock() {
        return (region != null && !region.isBlank())
            || (profile != null && !profile.isBlank())
            || (
                accessKeyId != null && !accessKeyId.isBlank()
                    && secretAccessKey != null && !secretAccessKey.isBlank()
            );
    }
}
