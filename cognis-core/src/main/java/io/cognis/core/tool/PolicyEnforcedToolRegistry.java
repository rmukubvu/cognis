package io.cognis.core.tool;

import io.cognis.core.sandbox.VerticalPolicy;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link ToolRegistry} view that enforces a {@link VerticalPolicy}.
 *
 * <p>Wraps the global registry and filters it to only the tools the vertical declared.
 * A vertical calling {@code find("shell")} when its policy allows only
 * {@code "supply_tracking"} receives an empty {@code Optional} — exactly as if the
 * tool does not exist.
 *
 * <p>Registration ({@link #register}) always succeeds — a vertical's own tools are
 * allowed by definition and pass through to the delegate.
 */
public class PolicyEnforcedToolRegistry extends ToolRegistry {

    private final ToolRegistry delegate;
    private final VerticalPolicy policy;
    private final String verticalName;

    public PolicyEnforcedToolRegistry(ToolRegistry delegate, VerticalPolicy policy, String verticalName) {
        this.delegate     = delegate;
        this.policy       = policy;
        this.verticalName = verticalName;
    }

    @Override
    public void register(Tool tool) {
        delegate.register(tool);
    }

    @Override
    public Optional<Tool> find(String name) {
        if (!policy.allowsTool(name)) {
            return Optional.empty();
        }
        return delegate.find(name);
    }

    @Override
    public Collection<Tool> all() {
        if (policy.isPermissive()) {
            return delegate.all();
        }
        return delegate.all().stream()
            .filter(tool -> policy.allowsTool(tool.name()))
            .collect(Collectors.toList());
    }

    /** The vertical name this registry is scoped to — for logging and diagnostics. */
    public String verticalName() {
        return verticalName;
    }

    /** The policy being enforced. */
    public VerticalPolicy policy() {
        return policy;
    }
}
