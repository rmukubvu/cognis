package io.cognis.mcp.server.router;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.mcp.server.ToolRouter;
import io.cognis.mcp.server.model.ToolCallResponse;
import io.cognis.mcp.server.model.ToolDefinition;
import io.cognis.mcp.server.provider.IntegrationProvider;
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
}
