package io.cognis.core.provider;

import io.cognis.core.model.ToolCall;
import java.util.List;
import java.util.Map;

public record LlmResponse(String content, List<ToolCall> toolCalls, Map<String, Object> usage) {
    public LlmResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Map.of() : Map.copyOf(usage);
    }
}
