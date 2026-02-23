package io.cognis.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WorkspaceBootstrap {

    private WorkspaceBootstrap() {
    }

    public static void ensureWorkspaceTemplates(Path workspace) throws IOException {
        Files.createDirectories(workspace);

        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("AGENTS.md", "# Agent Instructions\n\nYou are a precise AI assistant.\n");
        templates.put("SOUL.md", "# Soul\n\nI am Cognis.\n");
        templates.put("USER.md", "# User\n\nAdd user preferences here.\n");

        for (Map.Entry<String, String> entry : templates.entrySet()) {
            Path path = workspace.resolve(entry.getKey());
            if (!Files.exists(path)) {
                Files.writeString(path, entry.getValue(), StandardCharsets.UTF_8);
            }
        }

        Path memoryDir = workspace.resolve("memory");
        Files.createDirectories(memoryDir);
        Path memoryFile = memoryDir.resolve("MEMORY.md");
        if (!Files.exists(memoryFile)) {
            Files.writeString(memoryFile, "# Long-term Memory\n\n", StandardCharsets.UTF_8);
        }
    }
}
