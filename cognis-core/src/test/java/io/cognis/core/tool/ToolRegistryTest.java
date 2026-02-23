package io.cognis.core.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    @Test
    void shouldRegisterAndResolveTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());

        assertThat(registry.find("echo")).isPresent();
        assertThat(registry.find("echo").orElseThrow().execute(Map.of("text", "ok"), new ToolContext(null)))
            .isEqualTo("ok");
    }

    private static final class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Echo tool";
        }

        @Override
        public String execute(Map<String, Object> input, ToolContext context) {
            return String.valueOf(input.getOrDefault("text", ""));
        }
    }
}
