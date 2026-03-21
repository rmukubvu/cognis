package io.cognis.vertical.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExampleToolTest {

    @TempDir
    Path tempDir;

    @Test
    void nameFollowsDotNotation() {
        assertThat(new ExampleTool().name()).contains(".");
    }

    @Test
    void descriptionIsNotBlank() {
        assertThat(new ExampleTool().description()).isNotBlank();
    }

    @Test
    void schemaHasTypeObject() {
        Map<String, Object> schema = new ExampleTool().schema();
        assertThat(schema).containsEntry("type", "object");
        assertThat(schema).containsKey("properties");
    }

    @Test
    void executeReturnsNonNullResult() {
        ToolContext ctx = new ToolContext(tempDir);
        String result = new ExampleTool().execute(Map.of("query", "hello"), ctx);
        assertThat(result).isNotNull();
    }
}
