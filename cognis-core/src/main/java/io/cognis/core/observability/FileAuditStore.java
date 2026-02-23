package io.cognis.core.observability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class FileAuditStore implements AuditStore {
    private final Path path;
    private final ObjectMapper mapper;

    public FileAuditStore(Path path) {
        this.path = path;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public synchronized List<AuditEvent> load() throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return mapper.readValue(Files.readString(path), new TypeReference<List<AuditEvent>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @Override
    public synchronized void save(List<AuditEvent> events) throws IOException {
        Files.createDirectories(path.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(events);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json + System.lineSeparator());
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
