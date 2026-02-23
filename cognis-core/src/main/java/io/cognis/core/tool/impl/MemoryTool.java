package io.cognis.core.tool.impl;

import io.cognis.core.memory.MemoryEntry;
import io.cognis.core.memory.MemoryStore;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MemoryTool implements Tool {

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public String description() {
        return "Manage long-term memory: remember, recall, forget, list";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        MemoryStore store = context.service("memoryStore", MemoryStore.class);
        if (store == null) {
            return "Error: memory store is not configured";
        }

        String action = String.valueOf(input.getOrDefault("action", "")).trim();
        try {
            return switch (action) {
                case "remember" -> remember(store, input);
                case "recall" -> recall(store, input);
                case "forget" -> forget(store, input);
                case "list" -> list(store, input);
                default -> "Error: unknown action: " + action;
            };
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String remember(MemoryStore store, Map<String, Object> input) throws Exception {
        String content = String.valueOf(input.getOrDefault("content", "")).trim();
        if (content.isBlank()) {
            return "Error: content is required";
        }

        List<String> tags = extractTags(input.get("tags"));
        MemoryEntry entry = store.remember(content, "agent", tags);
        return "Memory stored (id: " + entry.id() + ")";
    }

    private String recall(MemoryStore store, Map<String, Object> input) throws Exception {
        String query = String.valueOf(input.getOrDefault("query", ""));
        int max = toInt(input.get("maxResults"), 10);

        List<MemoryEntry> entries = store.recall(query, max);
        if (entries.isEmpty()) {
            return "No matching memories found";
        }

        List<String> lines = new ArrayList<>();
        for (MemoryEntry entry : entries) {
            String tags = entry.tags().isEmpty() ? "" : " (" + String.join(", ", entry.tags()) + ")";
            lines.add("- [" + entry.id() + "] " + entry.content() + tags);
        }
        return String.join("\n", lines);
    }

    private String forget(MemoryStore store, Map<String, Object> input) throws Exception {
        String id = String.valueOf(input.getOrDefault("id", "")).trim();
        if (id.isBlank()) {
            return "Error: id is required";
        }
        return store.forget(id) ? "Memory removed: " + id : "Memory not found: " + id;
    }

    private String list(MemoryStore store, Map<String, Object> input) throws Exception {
        int max = toInt(input.get("maxResults"), 20);
        List<MemoryEntry> entries = store.recall("", max);
        if (entries.isEmpty()) {
            return "No memories stored";
        }

        List<String> lines = new ArrayList<>();
        lines.add("Stored memories (" + store.count() + " total):");
        for (MemoryEntry entry : entries) {
            String tags = entry.tags().isEmpty() ? "" : " (" + String.join(", ", entry.tags()) + ")";
            lines.add("- [" + entry.id() + "] " + entry.content() + tags);
        }
        return String.join("\n", lines);
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<String> extractTags(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (Object item : list) {
            String token = String.valueOf(item).trim();
            if (!token.isBlank()) {
                tags.add(token);
            }
        }
        return tags;
    }
}
