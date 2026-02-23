package io.cognis.core.tool.impl;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;

public final class WorkspaceGuard {

    public Path resolve(ToolContext context, String relativeOrAbsolutePath) {
        if (context.workspace() == null) {
            throw new IllegalArgumentException("Workspace is not configured");
        }

        Path workspace = context.workspace().toAbsolutePath().normalize();
        Path requested = Path.of(relativeOrAbsolutePath);
        Path resolved = requested.isAbsolute()
            ? requested.toAbsolutePath().normalize()
            : workspace.resolve(requested).normalize();

        if (!resolved.startsWith(workspace)) {
            throw new IllegalArgumentException("Path escapes workspace: " + relativeOrAbsolutePath);
        }

        return resolved;
    }
}
