package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import io.cognis.mcp.server.model.ToolCallResponse;
import io.cognis.mcp.server.model.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractHttpIntegrationProvider implements IntegrationProvider {
    private final ProviderConfig config;
    private final ProviderHttpClient httpClient;
    private final Map<String, ProviderOperation> operations;

    protected AbstractHttpIntegrationProvider(ProviderConfig config, ProviderHttpClient httpClient, List<ProviderOperation> operations) {
        this.config = config;
        this.httpClient = httpClient;
        this.operations = new LinkedHashMap<>();
        for (ProviderOperation operation : operations) {
            this.operations.put(operation.toolName(), operation);
        }
    }

    @Override
    public List<ToolDefinition> tools() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (ProviderOperation operation : operations.values()) {
            definitions.add(
                new ToolDefinition(
                    operation.toolName(),
                    operation.description() + " (provider configured=" + config.configured() + ")",
                    Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "query", Map.of("type", "object"),
                            "body", Map.of("type", "object")
                        ),
                        "additionalProperties", false
                    ),
                    name(),
                    operation.mutating()
                )
            );
        }
        return definitions;
    }

    @Override
    public ToolCallResponse execute(String toolName, Map<String, Object> arguments) {
        ProviderOperation operation = operations.get(toolName);
        if (operation == null) {
            return ToolCallResponse.error("Unknown tool: " + toolName);
        }
        if (!config.configured()) {
            return ToolCallResponse.error(
                "Provider not configured for " + name() + ". Set env vars for credentials before calling mutating APIs."
            );
        }
        try {
            Map<String, String> query = toStringMap(arguments.get("query"));
            Map<String, Object> body = toObjectMap(arguments.get("body"));
            Map<String, String> headers = new LinkedHashMap<>(config.authorizationHeaders());
            headers.put("Content-Type", "application/json");

            Map<String, Object> response = httpClient.send(
                operation.method(),
                config.baseUrl(),
                resolvePath(operation.path()),
                query,
                headers,
                body
            );
            boolean ok = Boolean.TRUE.equals(response.get("ok"));
            String message = ok ? "Executed " + toolName : "Provider call failed for " + toolName;
            return ok ? ToolCallResponse.ok(message, response) : ToolCallResponse.error(message + ": " + response);
        } catch (Exception e) {
            return ToolCallResponse.error("Provider call failed for " + toolName + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private String resolvePath(String path) {
        String resolved = path;
        if (config.accountId() != null && !config.accountId().isBlank()) {
            resolved = resolved.replace("%ACCOUNT_ID%", config.accountId());
        }
        return resolved;
    }
}
