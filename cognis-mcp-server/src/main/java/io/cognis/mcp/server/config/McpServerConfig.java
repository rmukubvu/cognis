package io.cognis.mcp.server.config;

import java.time.Duration;
import java.util.Map;

public record McpServerConfig(
    int port,
    Duration timeout,
    Map<String, ProviderConfig> providers
) {
    public static McpServerConfig fromEnv() {
        return new McpServerConfig(
            intEnv("COGNIS_MCP_PORT", 8791),
            Duration.ofSeconds(intEnv("COGNIS_MCP_HTTP_TIMEOUT_SECONDS", 25)),
            Map.of(
                "stripe", provider("stripe", "https://api.stripe.com/v1"),
                "amazon", provider("amazon", "https://api.amazon.com"),
                "uber", provider("uber", "https://api.uber.com/v1"),
                "lyft", provider("lyft", "https://api.lyft.com/v1"),
                "instacart", provider("instacart", "https://connect.instacart.com"),
                "doordash", provider("doordash", "https://openapi.doordash.com"),
                "twilio", twilioProvider()
            )
        );
    }

    public ProviderConfig provider(String name) {
        return providers.get(name);
    }

    private static ProviderConfig provider(String name, String defaultBaseUrl) {
        String upper = name.toUpperCase();
        return new ProviderConfig(
            name,
            env(upper + "_BASE_URL", defaultBaseUrl),
            env(upper + "_API_KEY", ""),
            env(upper + "_ACCOUNT_ID", ""),
            env(upper + "_AUTH_TOKEN", "")
        );
    }

    private static ProviderConfig twilioProvider() {
        return new ProviderConfig(
            "twilio",
            env("TWILIO_BASE_URL", "https://api.twilio.com/2010-04-01"),
            "",
            env("TWILIO_ACCOUNT_SID", ""),
            env("TWILIO_AUTH_TOKEN", "")
        );
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intEnv(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
