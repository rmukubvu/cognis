package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.bus.InMemoryMessageBus;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPublishMessageToBus() {
        InMemoryMessageBus bus = new InMemoryMessageBus();
        MessageTool tool = new MessageTool();

        String result = tool.execute(
            Map.of("channel", "telegram", "content", "hello"),
            new ToolContext(tempDir, Map.of("messageBus", bus))
        );

        assertThat(result).contains("Queued message");
        var queued = bus.poll();
        assertThat(queued).isPresent();
        assertThat(queued.orElseThrow().content()).contains("[telegram] hello");
    }
}
