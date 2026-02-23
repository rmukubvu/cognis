package io.cognis.cli;

import picocli.CommandLine.Command;

@Command(name = "cognis", mixinStandardHelpOptions = true, description = "Cognis autonomous intelligence runtime")
public final class CognisCliCommand implements Runnable {

    @Override
    public void run() {
        // Root command only shows help when no subcommand is provided.
    }
}
