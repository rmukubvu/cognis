package io.cognis.core.model;

import java.util.List;
import java.util.Objects;

public record ChatMessage(MessageRole role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public ChatMessage {
        Objects.requireNonNull(role, "role must not be null");
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(MessageRole.SYSTEM, content, null, List.of());
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(MessageRole.USER, content, null, List.of());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(MessageRole.ASSISTANT, content, null, List.of());
    }

    public static ChatMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(MessageRole.ASSISTANT, content, null, toolCalls);
    }

    public static ChatMessage tool(String content, String toolCallId) {
        return new ChatMessage(MessageRole.TOOL, content, toolCallId, List.of());
    }
}
