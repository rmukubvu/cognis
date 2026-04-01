package io.cognis.core.bus;

import io.cognis.core.model.ChatMessage;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * In-process pub/sub message bus.
 * <p>
 * Supports topic-based fan-out: multiple subscribers can react to the same event
 * without coupling (e.g. WhatsApp reply sender, WebSocket dashboard, audit trail
 * all subscribe to {@code "channel.whatsapp"} independently).
 * <p>
 * The legacy {@link #publish(ChatMessage)} / {@link #poll()} methods are preserved
 * as defaults that route to the {@code "default"} topic, so existing callers compile
 * unchanged.
 */
public interface MessageBus {

    /**
     * Subscribe {@code listener} to all messages published on {@code topic}.
     *
     * @return a subscriptionId that can be passed to {@link #unsubscribe} to remove this listener
     */
    String subscribe(String topic, Consumer<BusMessage> listener);

    /** Remove a previously registered subscription. No-op if the ID is unknown. */
    void unsubscribe(String subscriptionId);

    /** Publish {@code message} to all current subscribers of {@code topic}. */
    void publish(String topic, BusMessage message);

    // -------------------------------------------------------------------------
    // Legacy API — routes to the implicit "default" topic
    // -------------------------------------------------------------------------

    /** @deprecated Use {@link #publish(String, BusMessage)} with an explicit topic. */
    @Deprecated
    default void publish(ChatMessage message) {
        publish("default", BusMessage.of("default", message));
    }

    /** @deprecated Subscribe to topic {@code "default"} instead. */
    @Deprecated
    default Optional<ChatMessage> poll() {
        return Optional.empty();
    }
}
