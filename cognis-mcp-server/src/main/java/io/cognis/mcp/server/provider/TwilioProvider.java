package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import io.cognis.mcp.server.model.ToolCallResponse;
import io.cognis.mcp.server.model.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TwilioProvider implements IntegrationProvider {
    private final ProviderConfig config;
    private final ProviderHttpClient httpClient;
    private final List<ToolDefinition> tools;

    public TwilioProvider(ProviderConfig config, ProviderHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.tools = List.of(
            new ToolDefinition(
                "twilio.send_sms",
                "Send SMS via Twilio",
                formInputSchema(),
                "twilio",
                true
            ),
            new ToolDefinition(
                "twilio.make_call",
                "Create voice call via Twilio",
                formInputSchema(),
                "twilio",
                true
            )
        );
    }

    @Override
    public String name() {
        return "twilio";
    }

    @Override
    public List<ToolDefinition> tools() {
        return tools;
    }

    @Override
    public ToolCallResponse execute(String toolName, Map<String, Object> arguments) {
        if (!supports(toolName)) {
            return ToolCallResponse.error("Unknown tool: " + toolName);
        }
        if (!config.configured()) {
            return ToolCallResponse.error("Provider not configured for twilio. Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN.");
        }

        String path = switch (toolName) {
            case "twilio.send_sms" -> "/Accounts/%ACCOUNT_ID%/Messages.json";
            case "twilio.make_call" -> "/Accounts/%ACCOUNT_ID%/Calls.json";
            default -> "";
        };

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawBody = arguments.get("body") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

            Map<String, String> form = new LinkedHashMap<>();
            rawBody.forEach((k, v) -> form.put(String.valueOf(k), String.valueOf(v)));

            Map<String, Object> response = httpClient.sendForm(
                "POST",
                config.baseUrl(),
                path.replace("%ACCOUNT_ID%", config.accountId()),
                Map.of(),
                config.authorizationHeaders(),
                form
            );

            boolean ok = Boolean.TRUE.equals(response.get("ok"));
            if (ok) {
                return ToolCallResponse.ok("Executed " + toolName, response);
            }
            return ToolCallResponse.error("Provider call failed for " + toolName + ": " + response);
        } catch (Exception e) {
            return ToolCallResponse.error("Provider call failed for " + toolName + ": " + e.getMessage());
        }
    }

    private Map<String, Object> formInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "body", Map.of("type", "object")
            ),
            "required", List.of("body"),
            "additionalProperties", false
        );
    }
}
