package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisionToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRequireConfiguration() {
        VisionTool tool = new VisionTool("", "", "gpt-4o");

        String result = tool.execute(Map.of("path", "a.png"), new ToolContext(tempDir));

        assertThat(result).contains("not configured");
    }
}
