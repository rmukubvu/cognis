package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteAndReadFileInWorkspace() {
        FilesystemTool tool = new FilesystemTool();
        ToolContext ctx = new ToolContext(tempDir);

        String write = tool.execute(Map.of("action", "write", "path", "notes/todo.txt", "content", "hello"), ctx);
        String read = tool.execute(Map.of("action", "read", "path", "notes/todo.txt"), ctx);

        assertThat(write).contains("Wrote");
        assertThat(read).isEqualTo("hello");
    }

    @Test
    void shouldBlockPathEscape() {
        FilesystemTool tool = new FilesystemTool();
        ToolContext ctx = new ToolContext(tempDir);

        String result = tool.execute(Map.of("action", "read", "path", "../outside.txt"), ctx);

        assertThat(result).contains("Path escapes workspace");
    }

    @Test
    void shouldListDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve("a"));
        Files.writeString(tempDir.resolve("b.txt"), "x");

        FilesystemTool tool = new FilesystemTool();
        String result = tool.execute(Map.of("action", "list", "path", "."), new ToolContext(tempDir));

        assertThat(result).contains("dir a").contains("file b.txt");
    }
}
