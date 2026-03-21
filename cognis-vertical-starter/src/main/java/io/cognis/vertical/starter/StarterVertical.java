package io.cognis.vertical.starter;

import io.cognis.core.provider.IntegrationProvider;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.sdk.CognisVertical;
import io.cognis.sdk.RouteDefinition;
import java.util.List;

/**
 * Entry point for a Cognis vertical plugin.
 *
 * <h2>Quick-start: 4 steps to ship your vertical</h2>
 *
 * <ol>
 *   <li><strong>Rename</strong> this class and its package to match your domain
 *       (e.g. {@code LegalVertical} in {@code io.cognis.vertical.legal}).</li>
 *   <li><strong>Implement</strong> {@link ExampleTool} and {@link ExampleRoute} — or delete them
 *       and add your own {@link Tool} and {@link RouteDefinition} implementations.</li>
 *   <li><strong>Wire</strong> any shared services in {@link #initialize(ToolContext)} so your
 *       tools can access them via {@code ctx.service("key", Type.class)}.</li>
 *   <li><strong>Register</strong> by updating
 *       {@code META-INF/services/io.cognis.sdk.CognisVertical} with this class's fully-qualified
 *       name. The runtime discovers it automatically via {@link java.util.ServiceLoader}.</li>
 * </ol>
 *
 * <h2>What you can contribute</h2>
 * <ul>
 *   <li>{@link #tools()} — callable by the LLM during agent execution</li>
 *   <li>{@link #routes()} — HTTP webhooks or API endpoints on the gateway server</li>
 *   <li>{@link #providers()} — MCP integration providers (Twilio, Stripe, etc.) — optional</li>
 * </ul>
 */
public final class StarterVertical implements CognisVertical {

    // TODO: rename to a short, unique, human-readable identifier (e.g. "legal", "retail")
    @Override
    public String name() {
        return "starter";
    }

    // TODO: return your Tool implementations
    @Override
    public List<Tool> tools() {
        return List.of(new ExampleTool());
    }

    // TODO: return your RouteDefinition implementations, or List.of() if none needed
    @Override
    public List<RouteDefinition> routes() {
        return List.of(new ExampleRoute());
    }

    // TODO: return IntegrationProvider implementations if you need MCP-backed tools,
    //       or leave this default (empty list) if you don't
    @Override
    public List<IntegrationProvider> providers() {
        return List.of();
    }

    /**
     * Called once during startup, after the {@link ToolContext} is fully assembled.
     *
     * <p>Use this hook to:
     * <ul>
     *   <li>Run schema migrations or load seed data</li>
     *   <li>Build shared services and register them for tool access:
     *       <pre>{@code
     * MyService svc = new MyService(ctx.workspace().resolve("data"));
     * // tools access it via: ctx.service("myService", MyService.class)
     *       }</pre>
     *       Note: to make a service available to tools via {@code ctx.service(...)}, the runtime
     *       must pass a mutable services map. If that isn't available, keep state in your tool
     *       class fields instead.
     *   </li>
     *   <li>Perform health checks against external dependencies</li>
     * </ul>
     */
    @Override
    public void initialize(ToolContext ctx) {
        // TODO: one-time initialisation — remove this method body if nothing is needed
    }
}
