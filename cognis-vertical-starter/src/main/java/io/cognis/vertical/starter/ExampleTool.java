package io.cognis.vertical.starter;

import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.List;
import java.util.Map;

/**
 * Template tool — rename this class and implement your domain logic.
 *
 * <h2>Checklist</h2>
 * <ol>
 *   <li>Rename the class (e.g. {@code MatterSearchTool}, {@code OrderLookupTool}).</li>
 *   <li>Update {@link #name()} — must be unique across all registered tools, use dot-notation
 *       (e.g. {@code "legal.matter_search"}, {@code "retail.order_lookup"}).</li>
 *   <li>Update {@link #description()} — the LLM reads this to decide when to call the tool.</li>
 *   <li>Update {@link #schema()} — declare every parameter the LLM should supply.</li>
 *   <li>Implement {@link #execute(Map, ToolContext)} — do the real work, return a plain-text
 *       result the LLM will read as the tool output.</li>
 * </ol>
 *
 * <h2>Accessing shared services</h2>
 * <pre>{@code
 * MyService svc = ctx.service("myService", MyService.class);
 * }</pre>
 * Services are registered when {@link StarterVertical#initialize(ToolContext)} runs. You can
 * inject anything your tool needs by putting it in the services map at boot time.
 */
public final class ExampleTool implements Tool {

    // TODO: rename to your tool's unique dot-namespaced identifier
    @Override
    public String name() {
        return "starter.example";
    }

    // TODO: write a clear, concise description — the LLM uses this to decide when to call the tool
    @Override
    public String description() {
        return "An example tool. Replace this description with what your tool actually does.";
    }

    // TODO: declare the parameters the LLM must supply when calling this tool
    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "query", Map.of(
                    "type", "string",
                    "description", "TODO: describe this parameter"
                )
            ),
            "required", List.of("query"),
            "additionalProperties", false
        );
    }

    // TODO: implement your domain logic here
    @Override
    public String execute(Map<String, Object> input, ToolContext ctx) {
        String query = (String) input.getOrDefault("query", "");

        // Example: access a shared service registered in StarterVertical.initialize()
        // MyService svc = ctx.service("myService", MyService.class);

        // TODO: replace with real logic
        return "ExampleTool received: " + query;
    }
}
