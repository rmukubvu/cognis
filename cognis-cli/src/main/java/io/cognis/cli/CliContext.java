package io.cognis.cli;

import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.config.ConfigService;
import java.nio.file.Path;

public record CliContext(
    AgentOrchestrator orchestrator,
    ConfigService configService,
    Path configPath,
    GatewayRunner gatewayRunner
) {
    public CliContext(AgentOrchestrator orchestrator, ConfigService configService, Path configPath) {
        this(orchestrator, configService, configPath, (port, workspace) -> {
            throw new UnsupportedOperationException("gateway runner is not configured");
        });
    }
}
