package io.cognis.core.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class FileMemoryStore implements MemoryStore {
    private static final int EMBEDDING_DIM = 256;

    private final Path path;
    private final ObjectMapper mapper;

    public FileMemoryStore(Path path) {
        this.path = path;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public synchronized MemoryEntry remember(String content, String source, List<String> tags) throws IOException {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }

        List<MemoryEntry> entries = new ArrayList<>(listInternal());
        String dedupeKey = normalized.toLowerCase(Locale.ROOT);
        for (MemoryEntry existing : entries) {
            if (existing.content() != null && existing.content().trim().toLowerCase(Locale.ROOT).equals(dedupeKey)) {
                return existing;
            }
        }

        List<String> safeTags = tags == null ? List.of() : List.copyOf(tags);
        Instant now = Instant.now();
        MemoryEntry entry = new MemoryEntry(
            UUID.randomUUID().toString(),
            normalized,
            safeTags,
            embed(normalized, safeTags),
            source == null ? "agent" : source,
            now,
            now
        );
        entries.add(entry);
        save(entries);
        return entry;
    }

    @Override
    public synchronized boolean forget(String id) throws IOException {
        List<MemoryEntry> entries = new ArrayList<>(listInternal());
        boolean removed = entries.removeIf(entry -> entry.id().equals(id));
        if (removed) {
            save(entries);
        }
        return removed;
    }

    @Override
    public synchronized List<MemoryEntry> recall(String query, int maxResults) throws IOException {
        List<MemoryEntry> entries = listInternal();
        if (entries.isEmpty()) {
            return List.of();
        }

        int limit = Math.max(1, maxResults);
        if (query == null || query.isBlank()) {
            return entries.stream()
                .sorted(Comparator.comparing(MemoryEntry::createdAt).reversed())
                .limit(limit)
                .toList();
        }

        List<String> terms = tokenize(query);
        List<Double> queryEmbedding = embed(query, List.of());
        return entries.stream()
            .map(entry -> Map.entry(entry, score(entry, terms, queryEmbedding)))
            .filter(pair -> pair.getValue() > 0)
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .toList();
    }

    @Override
    public synchronized List<MemoryEntry> list() throws IOException {
        return listInternal().stream()
            .sorted(Comparator.comparing(MemoryEntry::createdAt).reversed())
            .toList();
    }

    @Override
    public synchronized int count() throws IOException {
        return listInternal().size();
    }

    @Override
    public synchronized String formatContext(int maxEntries) throws IOException {
        List<MemoryEntry> entries = recall("", maxEntries);
        if (entries.isEmpty()) {
            return "";
        }
        return entries.stream()
            .map(entry -> {
                String tags = entry.tags().isEmpty() ? "" : " [" + String.join(", ", entry.tags()) + "]";
                return "- " + entry.content() + tags;
            })
            .collect(Collectors.joining("\n"));
    }

    private List<MemoryEntry> listInternal() throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return mapper.readValue(Files.readString(path), new TypeReference<List<MemoryEntry>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void save(List<MemoryEntry> entries) throws IOException {
        Files.createDirectories(path.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json + System.lineSeparator());
        Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private List<String> tokenize(String text) {
        String[] raw = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String token : raw) {
            if (token.length() > 1 && !STOP_WORDS.contains(token)) {
                out.add(token);
            }
        }
        return out;
    }

    private double score(MemoryEntry entry, List<String> terms, List<Double> queryEmbedding) {
        if (terms.isEmpty()) {
            return 0;
        }
        Map<String, Integer> contentTf = tf(tokenize(entry.content()));
        Map<String, Integer> tagTf = tf(tokenize(String.join(" ", entry.tags())));

        double score = 0;
        for (String term : terms) {
            score += Math.log1p(contentTf.getOrDefault(term, 0));
            score += 2 * Math.log1p(tagTf.getOrDefault(term, 0));
        }
        double cosine = cosine(queryEmbedding, safeEmbedding(entry));
        return score + (3.0 * cosine);
    }

    private Map<String, Integer> tf(List<String> tokens) {
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }
        return counts;
    }

    private List<Double> safeEmbedding(MemoryEntry entry) {
        if (entry.embedding() == null || entry.embedding().isEmpty()) {
            return embed(entry.content(), entry.tags());
        }
        return entry.embedding();
    }

    private List<Double> embed(String content, List<String> tags) {
        double[] vector = new double[EMBEDDING_DIM];
        String joined = (content == null ? "" : content) + " " + String.join(" ", tags == null ? List.of() : tags);
        for (String token : tokenize(joined)) {
            int index = Math.floorMod(token.hashCode(), EMBEDDING_DIM);
            vector[index] += 1.0;
        }

        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);

        List<Double> embedding = new ArrayList<>(EMBEDDING_DIM);
        if (norm == 0.0) {
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                embedding.add(0.0);
            }
            return embedding;
        }

        for (double value : vector) {
            embedding.add(value / norm);
        }
        return embedding;
    }

    private double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int dim = Math.min(a.size(), b.size());
        double dot = 0.0;
        for (int i = 0; i < dim; i++) {
            dot += a.get(i) * b.get(i);
        }
        return Math.max(0.0, dot);
    }

    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "and", "or", "is", "are", "was", "were", "to", "of", "in", "for", "on", "with",
        "at", "by", "from", "it", "this", "that", "these", "those", "be", "been", "being", "as", "if", "but",
        "not", "no", "you", "your", "we", "our", "they", "their", "he", "she", "his", "her"
    );
}
