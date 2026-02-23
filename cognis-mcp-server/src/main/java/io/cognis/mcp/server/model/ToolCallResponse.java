package io.cognis.mcp.server.model;

import java.util.Map;

public record ToolCallResponse(
    boolean ok,
    String message,
    Map<String, Object> data
) {
    public static ToolCallResponse ok(String message, Map<String, Object> data) {
        return new ToolCallResponse(true, message, data == null ? Map.of() : data);
    }

    public static ToolCallResponse error(String message) {
        return new ToolCallResponse(false, message, Map.of());
    }
}
