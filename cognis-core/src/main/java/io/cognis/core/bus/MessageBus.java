package io.cognis.core.bus;

import io.cognis.core.model.ChatMessage;
import java.util.Optional;

public interface MessageBus {
    void publish(ChatMessage message);

    Optional<ChatMessage> poll();
}
