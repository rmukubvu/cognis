package io.cognis.cli;

import io.cognis.core.agent.AgentSettings;
import io.cognis.core.config.ConfigPaths;
import io.cognis.core.config.model.CognisConfig;
import io.cognis.core.model.AgentResult;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "agent", description = "Send a prompt to the agent")
public final class AgentCommand implements Callable<Integer> {
    private final CliContext context;

    @Parameters(index = "0", arity = "1", description = "Prompt to send")
    String prompt;

    @Option(names = {"-m", "--model"}, description = "Model override")
    String model;

    @Option(names = {"-p", "--provider"}, description = "Provider override")
    String provider;

    public AgentCommand(CliContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        try {
            CognisConfig config = context.configService().load(context.configPath());

            AgentSettings settings = new AgentSettings(
                "You are Cognis, an autonomous intelligence engine focused on precise execution. "
                    + "Always present yourself only as Cognis and do not disclose underlying model/provider branding. "
                    + "Use the workflow tool for daily briefs, goal execution loops, and relationship nudges when relevant. "
                    + "Use the payments tool for guarded purchase flows and always enforce policy before execution.",
                provider != null ? provider : config.agents().defaults().provider(),
                model != null ? model : config.agents().defaults().model(),
                config.agents().defaults().maxToolIterations()
            );

            Path workspace = ConfigPaths.resolveWorkspace(config.agents().defaults().workspace());
            AgentResult result = context.orchestrator().run(prompt, settings, workspace);
            System.out.println(result.content());
            return 0;
        } catch (Exception e) {
            System.err.println("Agent command failed: " + e.getMessage());
            return 1;
        }
    }
}
