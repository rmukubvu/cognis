package io.cognis.core.stratus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.memory.MemoryEntry;
import io.cognis.core.memory.MemoryStore;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link MemoryStore} implementation backed by the StratusOS VFS.
 *
 * <p>Storage layout on the VFS:
 * <pre>
 *   /verticals/{vertical}/memory/entries/{id}.json  — individual memory entries
 *   /verticals/{vertical}/memory/index.json         — lightweight entry list (id+content+tags)
 * </pre>
 *
 * <p>Semantic recall delegates to {@code POST /vfs/search} — StratusOS uses chromem-go
 * under the hood, so vector similarity search is built in with no external embedding API needed.
 *
 * <p>Falls back to a simple text-match recall if the VFS search endpoint returns no results,
 * maintaining parity with the local {@link io.cognis.core.memory.FileMemoryStore}.
 */
public final class StratusVfsMemoryStore implements MemoryStore {

    private final StratusClient stratus;
    private final String vfsBase;           // e.g. /verticals/humanitarian/memory
    private final ObjectMapper mapper;

    /**
     * @param stratus  configured StratusClient
     * @param vertical vertical name used to scope the VFS path (e.g. "humanitarian")
     */
    public StratusVfsMemoryStore(StratusClient stratus, String vertical) {
        this.stratus  = stratus;
        this.vfsBase  = "/verticals/" + vertical + "/memory";
        this.mapper   = new ObjectMapper();
    }

    // -------------------------------------------------------------------------
    // MemoryStore implementation
    // -------------------------------------------------------------------------

    @Override
    public synchronized MemoryEntry remember(String content, String source, List<String> tags) throws IOException {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }

        // Deduplication: skip if identical content already stored
        String dedupeKey = normalized.toLowerCase(Locale.ROOT);
        for (MemoryEntry existing : listInternal()) {
            if (existing.content().toLowerCase(Locale.ROOT).equals(dedupeKey)) {
                return existing;
            }
        }

        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            normalized,
            tags == null ? List.of() : List.copyOf(tags),
            List.of(),           // embeddings stored server-side in StratusOS VFS
            source == null ? "cognis" : source,
            Instant.now(),
            Instant.now()
        );

        // Write individual entry
        String entryPath = vfsBase + "/entries/" + entry.id() + ".json";
        stratus.vfsWrite(entryPath, mapper.writeValueAsString(entry));

        // Update lightweight index
        List<MemoryEntry> all = new ArrayList<>(listInternal());
        all.add(entry);
        stratus.vfsWrite(vfsBase + "/index.json", mapper.writeValueAsString(all));

        return entry;
    }

    @Override
    public synchronized boolean forget(String id) throws IOException {
        List<MemoryEntry> all = new ArrayList<>(listInternal());
        int before = all.size();
        all.removeIf(e -> e.id().equals(id));
        if (all.size() == before) {
            return false;
        }
        stratus.vfsWrite(vfsBase + "/index.json", mapper.writeValueAsString(all));
        return true;
    }

    @Override
    public List<MemoryEntry> recall(String query, int maxResults) throws IOException {
        if (query == null || query.isBlank()) {
            return list();
        }

        // Try StratusOS semantic search first
        String searchJson = stratus.vfsSearch(query, maxResults);
        List<MemoryEntry> semanticResults = parseSearchResults(searchJson);
        if (!semanticResults.isEmpty()) {
            return semanticResults;
        }

        // Fallback: keyword matching over local index
        String lower = query.toLowerCase(Locale.ROOT);
        return listInternal().stream()
            .filter(e -> e.content().toLowerCase(Locale.ROOT).contains(lower)
                || e.tags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(lower)))
            .limit(maxResults)
            .collect(Collectors.toList());
    }

    @Override
    public List<MemoryEntry> list() throws IOException {
        return Collections.unmodifiableList(listInternal());
    }

    @Override
    public int count() throws IOException {
        return listInternal().size();
    }

    @Override
    public String formatContext(int maxEntries) throws IOException {
        List<MemoryEntry> entries = listInternal();
        if (entries.isEmpty()) {
            return "";
        }
        int limit = Math.min(maxEntries, entries.size());
        StringBuilder sb = new StringBuilder("## Recalled memories\n");
        for (int i = 0; i < limit; i++) {
            MemoryEntry e = entries.get(i);
            sb.append("- ").append(e.content());
            if (!e.tags().isEmpty()) {
                sb.append(" [").append(String.join(", ", e.tags())).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<MemoryEntry> listInternal() throws IOException {
        String indexJson = stratus.vfsRead(vfsBase + "/index.json");
        if (indexJson == null || indexJson.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(indexJson, new TypeReference<List<MemoryEntry>>() {});
        } catch (Exception e) {
            // Index corrupt or empty — return empty rather than crashing
            return List.of();
        }
    }

    private List<MemoryEntry> parseSearchResults(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isArray() || root.isEmpty()) {
                return List.of();
            }
            List<MemoryEntry> results = new ArrayList<>();
            for (JsonNode node : root) {
                // StratusOS VFS search returns {path, content, score} chunks.
                // Wrap each chunk as a synthetic MemoryEntry for Cognis.
                String content = node.has("content") ? node.get("content").asText() : node.toString();
                String id      = node.has("path")    ? node.get("path").asText()    : UUID.randomUUID().toString();
                results.add(new MemoryEntry(id, content, List.of(), List.of(), "stratus-vfs", Instant.EPOCH, Instant.EPOCH));
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }
}
