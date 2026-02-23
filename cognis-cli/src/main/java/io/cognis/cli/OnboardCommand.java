package io.cognis.cli;

import io.cognis.core.config.OnboardResult;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "onboard", description = "Initialize or refresh config and workspace")
public final class OnboardCommand implements Callable<Integer> {
    private final CliContext context;

    @Option(names = "--overwrite", description = "Overwrite existing config with defaults")
    boolean overwrite;

    public OnboardCommand(CliContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        try {
            OnboardResult result = context.configService().onboard(context.configPath(), overwrite);
            if (result.createdConfig()) {
                System.out.println("Created config: " + result.configPath());
            } else if (result.overwrittenConfig()) {
                System.out.println("Overwrote config with defaults: " + result.configPath());
            } else {
                System.out.println("Refreshed config with new defaults: " + result.configPath());
            }
            System.out.println("Workspace ready: " + result.workspacePath());
            return 0;
        } catch (Exception e) {
            System.err.println("Onboard failed: " + e.getMessage());
            return 1;
        }
    }
}
