package io.cognis.core.model;

import java.util.List;
import java.util.Map;

public record AgentResult(String content, List<ChatMessage> transcript, Map<String, Object> usage) {
}
