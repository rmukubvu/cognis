package io.cognis.mcp.server.config;

import java.util.Base64;
import java.util.Map;

public record ProviderConfig(
    String name,
    String baseUrl,
    String apiKey,
    String accountId,
    String authToken
) {
    public boolean configured() {
        if (name.equals("twilio")) {
            return accountId != null && !accountId.isBlank() && authToken != null && !authToken.isBlank();
        }
        return apiKey != null && !apiKey.isBlank();
    }

    public Map<String, String> authorizationHeaders() {
        if (name.equals("twilio")) {
            if (!configured()) {
                return Map.of();
            }
            String token = Base64.getEncoder().encodeToString((accountId + ":" + authToken).getBytes());
            return Map.of("Authorization", "Basic " + token);
        }
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of();
        }
        return Map.of("Authorization", "Bearer " + apiKey);
    }
}
