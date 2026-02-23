package io.cognis.core.provider;

import io.cognis.core.model.ChatMessage;
import java.util.List;
import java.util.Map;

public interface LlmProvider {
    String name();

    LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools);
}
