package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExecuteSafeCommand() {
        ShellTool tool = new ShellTool(Duration.ofSeconds(2));
        String result = tool.execute(Map.of("command", "echo ok"), new ToolContext(tempDir));

        assertThat(result).contains("ok");
    }

    @Test
    void shouldBlockDangerousCommand() {
        ShellTool tool = new ShellTool(Duration.ofSeconds(2));
        String result = tool.execute(Map.of("command", "rm -rf /tmp/foo"), new ToolContext(tempDir));

        assertThat(result).contains("blocked by safety policy");
    }
}
