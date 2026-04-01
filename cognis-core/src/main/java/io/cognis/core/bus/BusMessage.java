package io.cognis.core.bus;

import io.cognis.core.model.ChatMessage;
import java.time.Instant;
import java.util.UUID;

/**
 * A message published to a specific topic on the {@link MessageBus}.
 * <p>
 * Wraps the existing {@link ChatMessage} with routing metadata so subscribers
 * can filter or attribute messages without inspecting payload content.
 */
public record BusMessage(String topic, String messageId, Instant publishedAt, ChatMessage payload) {

    public static BusMessage of(String topic, ChatMessage payload) {
        return new BusMessage(topic, UUID.randomUUID().toString(), Instant.now(), payload);
    }
}
