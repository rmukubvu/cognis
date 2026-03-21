package io.cognis.mcp.server.router;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.mcp.server.ToolRouter;
import io.cognis.core.provider.IntegrationProvider;
import io.cognis.core.provider.ToolCallResponse;
import io.cognis.core.provider.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRouterTest {
    @Test
    void routesToolCallsToCorrectProvider() {
        IntegrationProvider provider = new IntegrationProvider() {
            @Override
            public String name() {
                return "demo";
            }

            @Override
            public List<ToolDefinition> tools() {
                return List.of(new ToolDefinition("demo.echo", "Echo", Map.of(), "demo", false));
            }

            @Override
            public ToolCallResponse execute(String toolName, Map<String, Object> arguments) {
                return ToolCallResponse.ok("ok", Map.of("tool", toolName, "args", arguments));
            }
        };

        ToolRouter router = new ToolRouter(List.of(provider));
        ToolCallResponse response = router.callTool("demo.echo", Map.of("x", 1));

        assertThat(response.ok()).isTrue();
        assertThat(response.data()).containsEntry("tool", "demo.echo");
        assertThat(router.listTools()).extracting(ToolDefinition::name).contains("demo.echo");
    }

    @Test
    void returnsErrorForUnknownTool() {
        ToolRouter router = new ToolRouter(List.of());
        ToolCallResponse response = router.callTool("missing.tool", Map.of());

        assertThat(response.ok()).isFalse();
        assertThat(response.message()).contains("Unknown tool");
    }

    @Test
    void registerAfterConstructionAddsNewTools() {
        ToolRouter router = new ToolRouter(List.of());
        assertThat(router.listTools()).isEmpty();

        IntegrationProvider late = new IntegrationProvider() {
            @Override public String name() { return "late"; }
            @Override public List<ToolDefinition> tools() {
                return List.of(new ToolDefinition("late.ping", "Ping", Map.of(), "late", false));
            }
            @Override public ToolCallResponse execute(String toolName, Map<String, Object> arguments) {
                return ToolCallResponse.ok("pong", Map.of());
            }
        };

        router.register(late);

        assertThat(router.listTools()).extracting(ToolDefinition::name).containsExactly("late.ping");
        assertThat(router.callTool("late.ping", Map.of()).ok()).isTrue();
    }

    @Test
    void registerLastWinsOnDuplicateToolName() {
        IntegrationProvider first = new IntegrationProvider() {
            @Override public String name() { return "first"; }
            @Override public List<ToolDefinition> tools() {
                return List.of(new ToolDefinition("shared.op", "First", Map.of(), "first", false));
            }
            @Override public ToolCallResponse execute(String toolName, Map<String, Object> arguments) {
                return ToolCallResponse.ok("from-first", Map.of());
            }
        };

        IntegrationProvider second = new IntegrationProvider() {
            @Override public String name() { return "second"; }
            @Override public List<ToolDefinition> tools() {
                return List.of(new ToolDefinition("shared.op", "Second", Map.of(), "second", false));
            }
            @Override public ToolCallResponse execute(String toolName, Map<String, Object> arguments) {
                return ToolCallResponse.ok("from-second", Map.of());
            }
        };

        ToolRouter router = new ToolRouter(List.of(first));
        router.register(second);

        ToolCallResponse response = router.callTool("shared.op", Map.of());
        assertThat(response.ok()).isTrue();
        assertThat(response.message()).isEqualTo("from-second");
        // only one entry for the tool name
        assertThat(router.listTools()).extracting(ToolDefinition::name).containsExactly("shared.op");
    }
}
