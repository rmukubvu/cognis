package io.cognis.mcp.server.model;

import java.util.Map;

public record ToolDefinition(
    String name,
    String description,
    Map<String, Object> inputSchema,
    String provider,
    boolean mutating
) {
}
