package io.cognis.core.bus;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Topic-based in-process pub/sub {@link MessageBus}.
 * <p>
 * Design choices:
 * <ul>
 *   <li>{@link CopyOnWriteArrayList} per topic — safe to iterate while concurrent
 *       subscribe/unsubscribe occur; optimal when reads (publishes) far outnumber writes.</li>
 *   <li>Each listener runs on a fresh virtual thread — a slow subscriber (e.g. SQLite audit write)
 *       cannot block the publisher or other subscribers.</li>
 *   <li>A secondary {@code ConcurrentHashMap<subscriptionId, topic>} index makes
 *       {@link #unsubscribe} O(1) topic lookup + O(n) list scan (n = subscribers per topic,
 *       typically very small).</li>
 * </ul>
 * <p>
 * Replaces {@link InMemoryMessageBus}. Existing callers of {@link #publish(io.cognis.core.model.ChatMessage)}
 * and {@link #poll()} continue to work via the interface default methods.
 */
public final class TopicMessageBus implements MessageBus {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Entry>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> subscriptionIndex = new ConcurrentHashMap<>();

    @Override
    public String subscribe(String topic, Consumer<BusMessage> listener) {
        String id = UUID.randomUUID().toString();
        subscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                   .add(new Entry(id, listener));
        subscriptionIndex.put(id, topic);
        return id;
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        String topic = subscriptionIndex.remove(subscriptionId);
        if (topic == null) return;
        CopyOnWriteArrayList<Entry> list = subscribers.get(topic);
        if (list != null) list.removeIf(e -> e.id().equals(subscriptionId));
    }

    @Override
    public void publish(String topic, BusMessage message) {
        CopyOnWriteArrayList<Entry> list = subscribers.get(topic);
        if (list == null || list.isEmpty()) return;
        for (Entry entry : list) {
            Thread.ofVirtual().start(() -> entry.listener().accept(message));
        }
    }

    private record Entry(String id, Consumer<BusMessage> listener) {}
}
