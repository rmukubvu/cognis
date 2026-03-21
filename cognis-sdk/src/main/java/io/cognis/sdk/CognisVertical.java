package io.cognis.sdk;

import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.provider.IntegrationProvider;
import io.cognis.core.sandbox.VerticalPolicy;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.List;

/**
 * Entry point for a Cognis vertical plugin.
 *
 * <p>A vertical encapsulates all domain-specific behavior for a single customer or use-case
 * (e.g. humanitarian field reporting, law firm matter management, retail fulfilment). It
 * contributes tools, HTTP routes, and MCP integration providers to the Cognis runtime without
 * requiring any changes to {@code cognis-core} or {@code cognis-app}.
 *
 * <h2>Discovery</h2>
 * Implementations are discovered at runtime via {@link java.util.ServiceLoader}. Declare your
 * implementation in:
 * <pre>
 *   META-INF/services/io.cognis.sdk.CognisVertical
 * </pre>
 * Each line in that file should be the fully-qualified class name of one implementation.
 *
 * <h2>Dependency</h2>
 * Vertical JARs need only depend on {@code cognis-sdk}. They receive {@code cognis-core} types
 * ({@link Tool}, {@link ToolContext}, {@link IntegrationProvider}) transitively.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #initialize(ToolContext)} — called once after all core services are assembled.</li>
 *   <li>{@link #tools()} — registered into the shared {@code ToolRegistry}.</li>
 *   <li>{@link #routes()} — registered as HTTP routes on the gateway server.</li>
 *   <li>{@link #providers()} — registered into the MCP {@code ToolRouter}.</li>
 *   <li>{@link #heartbeatJobs()} — scheduled cron jobs fired proactively by the runtime.</li>
 *   <li>{@link #policy()} — declares allowed tools and hosts for sandbox enforcement.</li>
 * </ol>
 */
public interface CognisVertical {

    /**
     * Unique, human-readable identifier for this vertical.
     * Used in logging and diagnostics. Example: {@code "humanitarian"}, {@code "legal"}.
     */
    String name();

    /**
     * Tools this vertical contributes to the agent's tool registry.
     * These become callable by the LLM during agent execution.
     */
    List<Tool> tools();

    /**
     * HTTP routes to register with the Cognis gateway server.
     * Use this to expose webhooks (SMS, WhatsApp callbacks) or custom API endpoints.
     */
    List<RouteDefinition> routes();

    /**
     * MCP integration providers to register with the tool router.
     * Leave empty if this vertical does not expose MCP-backed tools.
     *
     * @return providers, or an empty list
     */
    default List<IntegrationProvider> providers() {
        return List.of();
    }

    /**
     * Cron-scheduled jobs this vertical wants the runtime to fire proactively.
     * Jobs run on dedicated daemon threads — they do not go through the LLM agent loop.
     * Use them for: overdue-shipment alerts, morning briefings, inbox polling.
     *
     * @return jobs, or an empty list
     */
    default List<HeartbeatJob> heartbeatJobs() {
        return List.of();
    }

    /**
     * Declares the sandbox policy for this vertical: which tools it may call and
     * which external hosts it may contact. Enforced at registration time by the runtime.
     *
     * <p>Defaults to {@link VerticalPolicy#permissive()} (no restrictions). Set explicit
     * constraints before going multi-tenant.
     */
    default VerticalPolicy policy() {
        return VerticalPolicy.permissive();
    }

    /**
     * Called once during application startup, after the {@link ToolContext} is fully assembled.
     * Use this hook for one-time initialization: schema migrations, asset loading, health checks.
     *
     * <p>The default implementation does nothing.
     *
     * @param context the fully-assembled tool context, including workspace path and shared services
     */
    default void initialize(ToolContext context) {}
}
