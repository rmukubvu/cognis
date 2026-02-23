package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.bus.InMemoryMessageBus;
import io.cognis.core.cron.CronService;
import io.cognis.core.cron.FileCronStore;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotifyToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSendImmediateNotification() {
        NotifyTool tool = new NotifyTool();
        InMemoryMessageBus bus = new InMemoryMessageBus();

        String result = tool.execute(
            Map.of("message", "hello"),
            new ToolContext(tempDir, Map.of("messageBus", bus))
        );

        assertThat(result).contains("immediately");
        assertThat(bus.poll()).isPresent();
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
