package io.cognis.core.sandbox;

import java.util.Set;

/**
 * Declares the sandbox constraints for a vertical plugin.
 *
 * <p>The Cognis runtime enforces these at startup:
 * <ul>
 *   <li>{@link #allowedTools()} — only these tool names are visible to the vertical's agent.
 *       Calls to tools outside this set return an error. Empty set = allow all.</li>
 *   <li>{@link #allowedHosts()} — reserved for future network-layer enforcement.
 *       Stored now so policy can be audited without code changes. Empty set = allow all.</li>
 * </ul>
 *
 * <h2>Usage in a vertical</h2>
 * <pre>{@code
 * public VerticalPolicy policy() {
 *     return VerticalPolicy.ofTools(Set.of("supply_tracking", "memory", "notify"));
 * }
 * }</pre>
 *
 * <h2>Multi-tenant isolation</h2>
 * Each vertical receives a {@code PolicyEnforcedToolRegistry} scoped to its policy.
 * A humanitarian vertical declaring only {@code "supply_tracking"} cannot call
 * {@code "shell"} or any other core tool — even if those tools are globally registered.
 */
public record VerticalPolicy(Set<String> allowedTools, Set<String> allowedHosts) {

    private static final VerticalPolicy PERMISSIVE = new VerticalPolicy(Set.of(), Set.of());

    /**
     * Create a policy with explicit allow-lists.
     *
     * @param allowedTools tool names this vertical may invoke (empty = allow all)
     * @param allowedHosts external hostnames this vertical may contact (empty = allow all)
     */
    public static VerticalPolicy of(Set<String> allowedTools, Set<String> allowedHosts) {
        return new VerticalPolicy(Set.copyOf(allowedTools), Set.copyOf(allowedHosts));
    }

    /** Convenience: tool-only policy with no host restrictions. */
    public static VerticalPolicy ofTools(Set<String> allowedTools) {
        return new VerticalPolicy(Set.copyOf(allowedTools), Set.of());
    }

    /** No restrictions — suitable for development and single-tenant deployments. */
    public static VerticalPolicy permissive() {
        return PERMISSIVE;
    }

    /**
     * Returns {@code true} if the given tool name is permitted.
     * An empty {@link #allowedTools()} set is treated as "allow all".
     */
    public boolean allowsTool(String toolName) {
        return allowedTools.isEmpty() || allowedTools.contains(toolName);
    }

    /**
     * Returns {@code true} if the given hostname is permitted.
     * An empty {@link #allowedHosts()} set is treated as "allow all".
     */
    public boolean allowsHost(String hostname) {
        return allowedHosts.isEmpty() || allowedHosts.contains(hostname);
    }

    /** Returns {@code true} if this policy imposes no restrictions. */
    public boolean isPermissive() {
        return allowedTools.isEmpty() && allowedHosts.isEmpty();
    }
}
