package io.cognis.core.provider;

import io.cognis.core.model.ChatMessage;
import java.util.List;
import java.util.Map;

public final class EchoProvider implements LlmProvider {
    private final String name;

    public EchoProvider(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
        String lastUserMessage = messages.stream()
            .filter(message -> message.role().name().equals("USER"))
            .reduce((first, second) -> second)
            .map(ChatMessage::content)
            .orElse("");

        return new LlmResponse("[" + name + "] " + lastUserMessage, List.of(), Map.of("provider", name));
    }
}
