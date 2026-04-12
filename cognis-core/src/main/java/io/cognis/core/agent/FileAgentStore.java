package io.cognis.core.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JSON-file backed {@link AgentStore}.
 *
 * <p>All agents are stored in a single JSON array at the configured path.
 * Suitable for small numbers of named agents (up to a few hundred).
 * Thread-safe via synchronization on {@code this}.
 */
public final class FileAgentStore implements AgentStore {

    private final Path path;
    private final ObjectMapper mapper;

    public FileAgentStore(Path path) {
        this.path   = path;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public synchronized void save(DynamicAgent agent) throws IOException {
        List<DynamicAgent> all = new ArrayList<>(listInternal());
        all.removeIf(a -> a.name().equals(agent.name()));
        all.add(agent);
        write(all);
    }

    @Override
    public synchronized Optional<DynamicAgent> find(String name) throws IOException {
        return listInternal().stream()
            .filter(a -> a.name().equals(name))
            .findFirst();
    }

    @Override
    public synchronized List<DynamicAgent> list() throws IOException {
        return List.copyOf(listInternal());
    }

    @Override
    public synchronized boolean delete(String name) throws IOException {
        List<DynamicAgent> all = new ArrayList<>(listInternal());
        boolean removed = all.removeIf(a -> a.name().equals(name));
        if (removed) write(all);
        return removed;
    }

    // -------------------------------------------------------------------------

    private List<DynamicAgent> listInternal() throws IOException {
        if (!Files.exists(path)) return List.of();
        String json = Files.readString(path);
        if (json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<List<DynamicAgent>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private void write(List<DynamicAgent> agents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(agents));
    }
}
