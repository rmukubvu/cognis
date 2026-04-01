package io.cognis.core.bus;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.model.ChatMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TopicMessageBusTest {

    @Test
    void singleSubscriberReceivesMessage() throws InterruptedException {
        TopicMessageBus bus = new TopicMessageBus();
        AtomicReference<BusMessage> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        bus.subscribe("events", msg -> { captured.set(msg); latch.countDown(); });
        bus.publish("events", BusMessage.of("events", ChatMessage.user("hello")));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.get().topic()).isEqualTo("events");
        assertThat(captured.get().payload().content()).isEqualTo("hello");
    }

    @Test
    void fanOutToMultipleSubscribers() throws InterruptedException {
        TopicMessageBus bus = new TopicMessageBus();
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger count = new AtomicInteger(0);

        bus.subscribe("feed", m -> { count.incrementAndGet(); latch.countDown(); });
        bus.subscribe("feed", m -> { count.incrementAndGet(); latch.countDown(); });
        bus.subscribe("feed", m -> { count.incrementAndGet(); latch.countDown(); });

        bus.publish("feed", BusMessage.of("feed", ChatMessage.user("event")));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(count.get()).isEqualTo(3);
    }

    @Test
    void topicsAreIsolated() throws InterruptedException {
        TopicMessageBus bus = new TopicMessageBus();
        AtomicInteger aCount = new AtomicInteger(0);
        AtomicInteger bCount = new AtomicInteger(0);

        bus.subscribe("a", m -> aCount.incrementAndGet());
        bus.subscribe("b", m -> bCount.incrementAndGet());

        bus.publish("a", BusMessage.of("a", ChatMessage.user("msg")));
        Thread.sleep(200);

        assertThat(aCount.get()).isEqualTo(1);
        assertThat(bCount.get()).isEqualTo(0);
    }

    @Test
    void unsubscribeStopsDelivery() throws InterruptedException {
        TopicMessageBus bus = new TopicMessageBus();
        AtomicInteger count = new AtomicInteger(0);

        String subId = bus.subscribe("ping", m -> count.incrementAndGet());
        bus.publish("ping", BusMessage.of("ping", ChatMessage.user("1")));
        Thread.sleep(200);

        bus.unsubscribe(subId);
        bus.publish("ping", BusMessage.of("ping", ChatMessage.user("2")));
        Thread.sleep(200);

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void publishToTopicWithNoSubscribersIsNoOp() {
        TopicMessageBus bus = new TopicMessageBus();
        // Should not throw
        bus.publish("nobody-listening", BusMessage.of("nobody-listening", ChatMessage.user("ignored")));
    }

    @Test
    void legacyPublishRoutesToDefaultTopic() throws InterruptedException {
        TopicMessageBus bus = new TopicMessageBus();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> topic = new AtomicReference<>();

        bus.subscribe("default", msg -> { topic.set(msg.topic()); latch.countDown(); });
        bus.publish(ChatMessage.user("legacy"));  // deprecated method

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(topic.get()).isEqualTo("default");
    }
}
