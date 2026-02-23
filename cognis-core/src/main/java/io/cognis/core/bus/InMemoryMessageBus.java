package io.cognis.core.bus;

import io.cognis.core.model.ChatMessage;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class InMemoryMessageBus implements MessageBus {
    private final ConcurrentLinkedQueue<ChatMessage> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void publish(ChatMessage message) {
        queue.offer(message);
    }

    @Override
    public Optional<ChatMessage> poll() {
        return Optional.ofNullable(queue.poll());
    }
}
