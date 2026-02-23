package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.integration.mcp.McpInvoker;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpToolTest {

    @TempDir
    Path tempDir;

    @Test
    void listToolsReturnsPayloadFromInvoker() {
        McpTool tool = new McpTool();
        McpInvoker invoker = new McpInvoker() {
            @Override
            public Map<String, Object> listTools() {
                return Map.of("tools", java.util.List.of(Map.of("name", "twilio.send_sms")));
            }

            @Override
            public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
                return Map.of("ok", true);
            }
        };

        String out = tool.execute(Map.of("action", "list_tools"), new ToolContext(tempDir, Map.of("mcpInvoker", invoker)));
        assertThat(out).contains("twilio.send_sms");
    }

    @Test
    void callToolRequiresToolName() {
        McpTool tool = new McpTool();
        McpInvoker invoker = new McpInvoker() {
            @Override
            public Map<String, Object> listTools() {
                return Map.of();
            }

            @Override
            public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
                return Map.of();
            }
        };

        String out = tool.execute(Map.of("action", "call_tool"), new ToolContext(tempDir, Map.of("mcpInvoker", invoker)));
        assertThat(out).contains("tool is required");
    }
}
