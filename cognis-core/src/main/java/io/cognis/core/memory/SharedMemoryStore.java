package io.cognis.core.memory;

import java.io.IOException;
import java.util.List;

/**
 * Namespaced facade over an existing {@link MemoryStore} that enables agents within the
 * same spawn tree to share structured facts without passing raw context strings.
 * <p>
 * <strong>Convention:</strong> each entry is tagged {@code "shared:{namespace}:{key}"}.
 * The {@code namespace} is typically a {@code runId} or a logical stage name (e.g.
 * {@code "triage-2024"}) so entries from different workflow executions do not collide.
 * <p>
 * <strong>Typical usage in a multi-agent workflow:</strong>
 * <pre>{@code
 * // Upstream agent writes findings
 * sharedMemory.write("intake-run-123", "beneficiary_count", "47 people at Juba site 3");
 *
 * // Downstream agent reads them — the full bullet list is injected into its system prompt
 * // via AgentOrchestrator.buildSystemPrompt() calling getSummary(parentRunId)
 * String context = sharedMemory.getSummary("intake-run-123");
 * }</pre>
 */
public final class SharedMemoryStore {

    private final MemoryStore backing;

    public SharedMemoryStore(MemoryStore backing) {
        this.backing = backing;
    }

    /**
     * Write a named fact under {@code namespace}. Overwrites silently if content
     * is identical (deduplication is handled by the backing {@link FileMemoryStore}).
     *
     * @param namespace logical grouping, usually a runId or workflow stage name
     * @param key       short identifier for this fact within the namespace
     * @param content   the fact to store
     */
    public void write(String namespace, String key, String content) throws IOException {
        backing.remember(content, "shared", List.of(tag(namespace, key)));
    }

    /**
     * Returns all entries stored under {@code namespace} as a formatted markdown block
     * suitable for injection into an agent system prompt.
     *
     * @return empty string if no entries exist for this namespace
     */
    public String getSummary(String namespace) throws IOException {
        String prefix = "shared:" + namespace + ":";
        List<MemoryEntry> entries = backing.list().stream()
            .filter(e -> e.tags() != null && e.tags().stream().anyMatch(t -> t.startsWith(prefix)))
            .toList();

        if (entries.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("## Shared context from upstream agents\n");
        for (MemoryEntry entry : entries) {
            // Extract the key from the tag for a readable label
            String label = entry.tags().stream()
                .filter(t -> t.startsWith(prefix))
                .map(t -> t.substring(prefix.length()))
                .findFirst().orElse("fact");
            sb.append("- **").append(label).append("**: ").append(entry.content()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns entries for a specific key within a namespace, or empty list if absent.
     */
    public List<MemoryEntry> read(String namespace, String key) throws IOException {
        String fullTag = tag(namespace, key);
        return backing.list().stream()
            .filter(e -> e.tags() != null && e.tags().contains(fullTag))
            .toList();
    }

    private static String tag(String namespace, String key) {
        return "shared:" + namespace + ":" + key;
    }
}
