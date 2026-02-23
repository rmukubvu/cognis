package io.cognis.core.tool.impl;

import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public final class FilesystemTool implements Tool {
    private final WorkspaceGuard guard = new WorkspaceGuard();

    @Override
    public String name() {
        return "filesystem";
    }

    @Override
    public String description() {
        return "Read, write, or list files in workspace";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of("type", "string", "enum", new String[] {"read", "write", "list"}),
                "path", Map.of("type", "string"),
                "content", Map.of("type", "string")
            ),
            "required", new String[] {"action", "path"}
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = String.valueOf(input.getOrDefault("action", "")).trim();
        String pathArg = String.valueOf(input.getOrDefault("path", "")).trim();

        if (action.isBlank() || pathArg.isBlank()) {
            return "Error: action and path are required";
        }

        try {
            Path target = guard.resolve(context, pathArg);
            return switch (action) {
                case "read" -> readFile(target);
                case "write" -> writeFile(target, String.valueOf(input.getOrDefault("content", "")));
                case "list" -> listDirectory(target);
                default -> "Error: unsupported action: " + action;
            };
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String readFile(Path target) throws IOException {
        if (!Files.exists(target)) {
            return "Error: file not found: " + target;
        }
        if (Files.isDirectory(target)) {
            return "Error: path is a directory: " + target;
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    private String writeFile(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return "Wrote " + target;
    }

    private String listDirectory(Path target) throws IOException {
        if (!Files.exists(target)) {
            return "Error: path not found: " + target;
        }
        if (!Files.isDirectory(target)) {
            return "Error: path is not a directory: " + target;
        }

        try (var stream = Files.list(target)) {
            return stream
                .sorted(Comparator.comparing(Path::toString))
                .map(path -> {
                    String type = Files.isDirectory(path) ? "dir" : "file";
                    return type + " " + target.relativize(path);
                })
                .collect(Collectors.joining("\n"));
        }
    }
}
