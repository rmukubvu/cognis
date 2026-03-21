package io.cognis.core.provider;

import java.util.Map;

public record ToolDefinition(
    String name,
    String description,
    Map<String, Object> inputSchema,
    String provider,
    boolean mutating
) {
}
