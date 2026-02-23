package io.cognis.cli;

import java.nio.file.Path;

@FunctionalInterface
public interface GatewayRunner {
    int run(int port, Path workspaceOverride) throws Exception;
}
