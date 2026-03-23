package io.cognis.core.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON-lines file-backed {@link UsageStore}.
 *
 * <p>Each {@link UsageRecord} is serialised as a single JSON object on its own line
 * (newline-delimited JSON / NDJSON). Writes are append-only; all public methods are
 * {@code synchronized} for thread safety.
 */
public final class FileUsageStore implements UsageStore {

    private final Path path;
    private final ObjectMapper mapper;

    /**
     * Creates a store that reads from and writes to {@code path}.
     * The parent directory is created on first write if it does not already exist.
     */
    public FileUsageStore(Path path) {
        this.path = path;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public synchronized void append(UsageRecord record) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(mapper.writeValueAsString(record));
            w.write('\n');
        }
    }

    @Override
    public synchronized List<UsageRecord> findAll() throws IOException {
        if (!Files.exists(path)) return List.of();
        List<UsageRecord> result = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (!line.isBlank()) {
                result.add(mapper.readValue(line, UsageRecord.class));
            }
        }
        return result;
    }

    @Override
    public synchronized List<UsageRecord> findSince(Instant since) throws IOException {
        List<UsageRecord> all = findAll();
        return all.stream().filter(r -> !r.timestamp().isBefore(since)).toList();
    }
}
