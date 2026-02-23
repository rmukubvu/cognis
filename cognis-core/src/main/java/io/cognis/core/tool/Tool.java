package io.cognis.core.tool;

import java.util.Map;

public interface Tool {
    String name();

    String description();

    default Map<String, Object> schema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    String execute(Map<String, Object> input, ToolContext context);
}
