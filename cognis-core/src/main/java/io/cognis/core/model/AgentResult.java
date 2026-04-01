package io.cognis.core.model;

import java.util.List;
import java.util.Map;

public record AgentResult(
    String content,
    List<ChatMessage> transcript,
    Map<String, Object> usage,
    AgentStatus status,
    String errorMessage
) {
    /** Backward-compatible constructor: status defaults to SUCCESS, no error message. */
    public AgentResult(String content, List<ChatMessage> transcript, Map<String, Object> usage) {
        this(content, transcript, usage, AgentStatus.SUCCESS, null);
    }

    public static AgentResult maxIterations(String content, List<ChatMessage> transcript, Map<String, Object> usage) {
        return new AgentResult(content, transcript, usage, AgentStatus.MAX_ITERATIONS, "Stopped after max tool iterations");
    }

    public boolean isSuccess() {
        return status == AgentStatus.SUCCESS;
    }
}
