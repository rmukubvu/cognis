package io.cognis.cli;

import io.cognis.core.config.ConfigPaths;
import io.cognis.core.config.model.CognisConfig;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "status", description = "Show runtime and configuration status")
public final class StatusCommand implements Callable<Integer> {
    private final CliContext context;

    public StatusCommand(CliContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        try {
            CognisConfig config = context.configService().load(context.configPath());
            System.out.println("Config path: " + context.configPath());
            System.out.println("Config exists: " + Files.exists(context.configPath()));
            System.out.println("Workspace: " + ConfigPaths.resolveWorkspace(config.agents().defaults().workspace()));
            System.out.println("Default provider: " + config.agents().defaults().provider());
            System.out.println("Default model: " + config.agents().defaults().model());
            System.out.println("OpenRouter configured: " + config.providers().openrouter().configured());
            System.out.println("OpenAI configured: " + config.providers().openai().configured());
            System.out.println("Anthropic configured: " + config.providers().anthropic().configured());
            System.out.println("OpenAI Codex configured: " + config.providers().openaiCodex().configured());
            System.out.println("Github Copilot configured: " + config.providers().githubCopilot().configured());
            return 0;
        } catch (Exception e) {
            System.err.println("Status command failed: " + e.getMessage());
            return 1;
        }
    }
}
