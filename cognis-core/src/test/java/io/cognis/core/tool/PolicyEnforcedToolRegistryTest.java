package io.cognis.core.tool;

import io.cognis.core.sandbox.VerticalPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEnforcedToolRegistryTest {

    // Minimal Tool stub
    private static Tool stub(String name) {
        return new Tool() {
            @Override public String name()        { return name; }
            @Override public String description() { return name + " tool"; }
            @Override public Map<String, Object> schema() { return Map.of(); }
            @Override public String execute(Map<String, Object> input, ToolContext ctx) { return "ok"; }
        };
    }

    @Test
    void permissivePolicyAllowsAllTools() {
        ToolRegistry delegate = new ToolRegistry();
        delegate.register(stub("supply_tracking"));
        delegate.register(stub("shell"));

        PolicyEnforcedToolRegistry registry =
            new PolicyEnforcedToolRegistry(delegate, VerticalPolicy.permissive(), "test");

        assertThat(registry.find("supply_tracking")).isPresent();
        assertThat(registry.find("shell")).isPresent();
        assertThat(registry.all()).hasSize(2);
    }

    @Test
    void restrictedPolicyHidesDisallowedTool() {
        ToolRegistry delegate = new ToolRegistry();
        delegate.register(stub("supply_tracking"));
        delegate.register(stub("shell"));
        delegate.register(stub("memory"));

        VerticalPolicy policy = VerticalPolicy.ofTools(Set.of("supply_tracking", "memory"));
        PolicyEnforcedToolRegistry registry =
            new PolicyEnforcedToolRegistry(delegate, policy, "humanitarian");

        assertThat(registry.find("supply_tracking")).isPresent();
        assertThat(registry.find("memory")).isPresent();
        assertThat(registry.find("shell")).isEmpty();  // blocked
    }

    @Test
    void allReturnsOnlyAllowedTools() {
        ToolRegistry delegate = new ToolRegistry();
        delegate.register(stub("supply_tracking"));
        delegate.register(stub("shell"));
        delegate.register(stub("payments"));

        VerticalPolicy policy = VerticalPolicy.ofTools(Set.of("supply_tracking"));
        PolicyEnforcedToolRegistry registry =
            new PolicyEnforcedToolRegistry(delegate, policy, "humanitarian");

        assertThat(registry.all())
            .extracting(Tool::name)
            .containsExactlyInAnyOrder("supply_tracking");
    }

    @Test
    void registeredToolGoesToDelegate() {
        ToolRegistry delegate = new ToolRegistry();
        VerticalPolicy policy = VerticalPolicy.ofTools(Set.of("my_tool"));
        PolicyEnforcedToolRegistry registry =
            new PolicyEnforcedToolRegistry(delegate, policy, "test");

        registry.register(stub("my_tool"));

        // Tool is findable through the scoped registry
        assertThat(registry.find("my_tool")).isPresent();
        // And also directly in delegate
        assertThat(delegate.find("my_tool")).isPresent();
    }

    @Test
    void verticalNameIsExposed() {
        ToolRegistry delegate = new ToolRegistry();
        PolicyEnforcedToolRegistry registry =
            new PolicyEnforcedToolRegistry(delegate, VerticalPolicy.permissive(), "humanitarian");
        assertThat(registry.verticalName()).isEqualTo("humanitarian");
    }

    @Test
    void allowsToolReturnsTrueForPermissivePolicy() {
        VerticalPolicy policy = VerticalPolicy.permissive();
        assertThat(policy.allowsTool("anything")).isTrue();
        assertThat(policy.allowsTool("shell")).isTrue();
        assertThat(policy.isPermissive()).isTrue();
    }

    @Test
    void allowsToolReturnsFalseForRestrictedPolicy() {
        VerticalPolicy policy = VerticalPolicy.ofTools(Set.of("supply_tracking"));
        assertThat(policy.allowsTool("supply_tracking")).isTrue();
        assertThat(policy.allowsTool("shell")).isFalse();
        assertThat(policy.isPermissive()).isFalse();
    }
}
