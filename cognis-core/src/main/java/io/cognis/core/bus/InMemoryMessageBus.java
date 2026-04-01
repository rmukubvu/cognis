package io.cognis.core.bus;

import io.cognis.core.model.ChatMessage;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @deprecated Use {@link TopicMessageBus} directly. This class is kept for backward compatibility
 * with any external code that instantiates it by name. All functionality delegates to
 * {@link TopicMessageBus}.
 */
@Deprecated
public final class InMemoryMessageBus implements MessageBus {
    private final TopicMessageBus delegate = new TopicMessageBus();

    @Override
    public String subscribe(String topic, Consumer<BusMessage> listener) {
        return delegate.subscribe(topic, listener);
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        delegate.unsubscribe(subscriptionId);
    }

    @Override
    public void publish(String topic, BusMessage message) {
        delegate.publish(topic, message);
    }

    @Override
    @Deprecated
    public void publish(ChatMessage message) {
        delegate.publish(message);
    }

    @Override
    @Deprecated
    public Optional<ChatMessage> poll() {
        return Optional.empty();
    }
}
