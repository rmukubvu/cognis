package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.bus.BusMessage;
import io.cognis.core.bus.TopicMessageBus;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPublishMessageToBus() throws InterruptedException {
        TopicMessageBus bus = new TopicMessageBus();
        MessageTool tool = new MessageTool();

        AtomicReference<BusMessage> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe("default", msg -> {
            captured.set(msg);
            latch.countDown();
        });

        String result = tool.execute(
            Map.of("channel", "telegram", "content", "hello"),
            new ToolContext(tempDir, Map.of("messageBus", bus))
        );

        assertThat(result).contains("Queued message");
        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).as("message should be received on 'default' topic").isTrue();
        assertThat(captured.get().payload().content()).contains("[telegram] hello");
    }
}
