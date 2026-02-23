package io.cognis.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.integration.mcp.McpInvoker;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.Map;

public final class McpTool implements Tool {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() {
        return "mcp";
    }

    @Override
    public String description() {
        return "Discover and call tools exposed by MCP servers (actions: list_tools, call_tool)";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "enum", new String[]{"list_tools", "call_tool"}
                ),
                "tool", Map.of("type", "string"),
                "arguments", Map.of("type", "object")
            ),
            "required", new String[]{"action"}
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        McpInvoker client = context.service("mcpInvoker", McpInvoker.class);
        if (client == null) {
            return "Error: mcp client is not configured";
        }

        try {
            String action = String.valueOf(input.getOrDefault("action", "")).trim();
            return switch (action) {
                case "list_tools" -> mapper.writerWithDefaultPrettyPrinter().writeValueAsString(client.listTools());
                case "call_tool" -> mapper.writerWithDefaultPrettyPrinter().writeValueAsString(callTool(client, input));
                default -> "Error: unknown action: " + action;
            };
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private Map<String, Object> callTool(McpInvoker client, Map<String, Object> input) throws Exception {
        String toolName = String.valueOf(input.getOrDefault("tool", "")).trim();
        if (toolName.isBlank()) {
            throw new IllegalArgumentException("tool is required for action=call_tool");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> args = input.get("arguments") instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : Map.of();
        return client.callTool(toolName, args);
    }
}
