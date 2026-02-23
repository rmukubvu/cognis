package io.cognis.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "gateway", description = "Start HTTP gateway for upload/transcribe endpoints")
public final class GatewayCommand implements Callable<Integer> {
    private final CliContext context;

    @Option(names = {"--port"}, description = "Gateway port", defaultValue = "8787")
    int port;

    @Option(names = {"--workspace"}, description = "Workspace override")
    Path workspace;

    public GatewayCommand(CliContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        try {
            return context.gatewayRunner().run(port, workspace);
        } catch (Exception e) {
            System.err.println("Gateway command failed: " + e.getMessage());
            return 1;
        }
    }
}
