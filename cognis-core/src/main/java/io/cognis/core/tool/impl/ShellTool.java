package io.cognis.core.tool.impl;

import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ShellTool implements Tool {
    private static final List<String> DENY_PATTERNS = List.of(
        "rm -rf /",
        "mkfs",
        "shutdown",
        "reboot",
        "curl http://169.254.",
        "wget http://169.254."
    );

    private final Duration timeout;

    public ShellTool(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "shell";
    }

    @Override
    public String description() {
        return "Execute shell commands inside workspace with safeguards";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of("command", Map.of("type", "string")),
            "required", new String[] {"command"}
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String command = String.valueOf(input.getOrDefault("command", "")).trim();
        if (command.isBlank()) {
            return "Error: command is required";
        }

        if (isDenied(command)) {
            return "Error: command blocked by safety policy";
        }

        if (context.workspace() == null) {
            return "Error: workspace is not configured";
        }

        try {
            Process process = new ProcessBuilder("/bin/sh", "-lc", command)
                .directory(context.workspace().toFile())
                .redirectErrorStream(true)
                .start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: command timed out";
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (output.length() > 12000) {
                output = output.substring(0, 12000) + "\n[truncated]";
            }
            return output.isBlank() ? "(no output)" : output;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return "Error: command interrupted";
        }
    }

    private boolean isDenied(String command) {
        String normalized = command.toLowerCase();
        for (String pattern : DENY_PATTERNS) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
