package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.bus.BusMessage;
import io.cognis.core.bus.TopicMessageBus;
import io.cognis.core.cron.CronService;
import io.cognis.core.cron.FileCronStore;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotifyToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSendImmediateNotification() throws InterruptedException {
        NotifyTool tool = new NotifyTool();
        TopicMessageBus bus = new TopicMessageBus();

        AtomicReference<BusMessage> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe("default", msg -> {
            captured.set(msg);
            latch.countDown();
        });

        String result = tool.execute(
            Map.of("message", "hello"),
            new ToolContext(tempDir, Map.of("messageBus", bus))
        );

        assertThat(result).contains("immediately");
        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).as("notification should be received on 'default' topic").isTrue();
        assertThat(captured.get().payload()).isNotNull();
    }

    @Test
    void shouldScheduleNotification() {
        NotifyTool tool = new NotifyTool();
        CronService cron = new CronService(new FileCronStore(tempDir.resolve("cron.json")), Clock.systemUTC());

        String result = tool.execute(
            Map.of("message", "digest", "inSeconds", 60),
            new ToolContext(tempDir, Map.of("cronService", cron))
        );

        assertThat(result).contains("scheduled");
    }
}
