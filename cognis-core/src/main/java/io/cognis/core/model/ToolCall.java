package io.cognis.core.model;

import java.util.Map;
import java.util.Objects;

public record ToolCall(String id, String name, Map<String, Object> arguments) {

    public ToolCall {
        Objects.requireNonNull(name, "name must not be null");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
