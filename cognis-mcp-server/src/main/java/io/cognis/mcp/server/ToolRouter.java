package io.cognis.mcp.server;

import io.cognis.core.provider.IntegrationProvider;
import io.cognis.core.provider.ToolCallResponse;
import io.cognis.core.provider.ToolDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolRouter {
    private final Map<String, IntegrationProvider> providerByTool;

    public ToolRouter(List<IntegrationProvider> providers) {
        this.providerByTool = new LinkedHashMap<>();
        for (IntegrationProvider provider : providers) {
            register(provider);
        }
    }

    public synchronized void register(IntegrationProvider provider) {
        for (ToolDefinition definition : provider.tools()) {
            providerByTool.put(definition.name(), provider);
        }
    }

    public synchronized List<ToolDefinition> listTools() {
        List<ToolDefinition> all = new ArrayList<>();
        for (Map.Entry<String, IntegrationProvider> entry : providerByTool.entrySet()) {
            String toolName = entry.getKey();
            IntegrationProvider provider = entry.getValue();
            provider.tools().stream().filter(tool -> tool.name().equals(toolName)).findFirst().ifPresent(all::add);
        }
        all.sort(Comparator.comparing(ToolDefinition::name));
        return all;
    }

    public synchronized ToolCallResponse callTool(String toolName, Map<String, Object> arguments) {
        IntegrationProvider provider = providerByTool.get(toolName);
        if (provider == null) {
            return ToolCallResponse.error("Unknown tool: " + toolName);
        }
        return provider.execute(toolName, arguments == null ? Map.of() : arguments);
    }
}
