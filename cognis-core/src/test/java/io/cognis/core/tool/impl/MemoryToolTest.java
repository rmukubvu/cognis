package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.memory.FileMemoryStore;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoryToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRememberAndRecall() {
        MemoryTool tool = new MemoryTool();
        FileMemoryStore store = new FileMemoryStore(tempDir.resolve("memories.json"));
        ToolContext context = new ToolContext(tempDir, Map.of("memoryStore", store));

        String remember = tool.execute(Map.of("action", "remember", "content", "User prefers markdown"), context);
        String recall = tool.execute(Map.of("action", "recall", "query", "markdown"), context);

        assertThat(remember).contains("Memory stored");
        assertThat(recall).contains("User prefers markdown");
    }
}
