package io.cognis.core.integration.mcp;

import java.util.Map;

public interface McpInvoker {
    Map<String, Object> listTools() throws Exception;

    Map<String, Object> callTool(String toolName, Map<String, Object> arguments) throws Exception;
}
