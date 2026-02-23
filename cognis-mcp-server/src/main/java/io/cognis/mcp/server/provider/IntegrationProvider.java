package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.model.ToolCallResponse;
import io.cognis.mcp.server.model.ToolDefinition;
import java.util.List;
import java.util.Map;

public interface IntegrationProvider {
    String name();

    List<ToolDefinition> tools();

    ToolCallResponse execute(String toolName, Map<String, Object> arguments);

    default boolean supports(String toolName) {
        return tools().stream().anyMatch(tool -> tool.name().equals(toolName));
    }
}
