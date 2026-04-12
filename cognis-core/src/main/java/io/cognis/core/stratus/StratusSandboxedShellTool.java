package io.cognis.core.stratus;

import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.Map;

/**
 * Drop-in replacement for {@link io.cognis.core.tool.impl.ShellTool} that routes
 * every shell command through StratusOS {@code POST /syscall} instead of spawning
 * a local {@code ProcessBuilder}.
 *
 * <p>Benefits over the local ShellTool:
 * <ul>
 *   <li>Policy enforcement — StratusOS rejects commands that violate the policy.toml rules</li>
 *   <li>Audit trail — every execution is immutably recorded in the bbolt ledger</li>
 *   <li>Landlock + seccomp — if sandbox.enabled, the agent process itself is kernel-contained</li>
 *   <li>Token-budget tracking — token cost counted at the kernel, not just at the LLM</li>
 *   <li>Portal trace view — the command appears in /agents/{id} timeline</li>
 * </ul>
 *
 * <p>Intent format sent to /syscall:
 * <pre>execute: {command}</pre>
 *
 * <p>StratusOS resolves this intent as {@code ACTION_COMPUTE_EXEC}. The agent session
 * must hold {@code CAP_COMPUTE_EXEC} or the call is denied and the error text is returned
 * to the LLM so it can reason about the refusal.
 */
public final class StratusSandboxedShellTool implements Tool {

    private final StratusClient stratus;

    public StratusSandboxedShellTool(StratusClient stratus) {
        this.stratus = stratus;
    }

    @Override
    public String name() {
        return "shell";
    }

    @Override
    public String description() {
        return "Execute shell commands via StratusOS policy enforcement (sandboxed, audited)";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of("command", Map.of("type", "string", "description", "Shell command to execute")),
            "required", new String[]{"command"}
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String command = String.valueOf(input.getOrDefault("command", "")).trim();
        if (command.isBlank()) {
            return "Error: command is required";
        }
        // Route through StratusOS /syscall — policy, ledger, sandbox all applied there.
        return stratus.syscall("execute: " + command);
    }
}
