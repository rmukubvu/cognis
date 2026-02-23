package io.cognis.mcp.server.model;

import java.util.Map;

public record ToolCallRequest(String name, Map<String, Object> arguments) {
}
