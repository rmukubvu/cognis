package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRequireBraveKeyForSearch() {
        WebTool tool = new WebTool("", 5);

        String result = tool.execute(Map.of("action", "search", "query", "java"), new ToolContext(tempDir));

        assertThat(result).contains("BRAVE_API_KEY not configured");
    }

    @Test
    void shouldBlockPrivateFetchTargets() {
        WebTool tool = new WebTool("", 5);

        String result = tool.execute(Map.of("action", "fetch", "url", "http://127.0.0.1:8080"), new ToolContext(tempDir));

        assertThat(result).contains("blocked");
    }
}
