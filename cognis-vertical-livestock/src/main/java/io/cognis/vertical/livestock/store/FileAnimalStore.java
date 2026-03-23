package io.cognis.vertical.livestock.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cognis.vertical.livestock.model.Animal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSON-backed {@link AnimalStore}.
 *
 * <p>Persists a {@code Map<String, Animal>} (id → animal) to a single JSON file.
 * Writes are atomic (write-to-tmp then rename). Thread-safe via {@code synchronized}.
 */
public final class FileAnimalStore implements AnimalStore {

    private static final TypeReference<Map<String, Animal>> MAP_TYPE = new TypeReference<>() {};

    private final Path path;
    private final ObjectMapper mapper;

    public FileAnimalStore(Path path) {
        this.path   = path;
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public synchronized void upsert(Animal animal) throws IOException {
        Map<String, Animal> all = load();
        all.put(animal.id(), animal);
        persist(all);
    }

    @Override
    public synchronized List<Animal> findAll() throws IOException {
        return List.copyOf(load().values());
    }

    @Override
    public synchronized Optional<Animal> findById(String id) throws IOException {
        return Optional.ofNullable(load().get(id));
    }

    @Override
    public synchronized List<Animal> findOutsideGeofence() throws IOException {
        return load().values().stream()
            .filter(a -> !a.insideGeofence())
            .toList();
    }

    @Override
    public synchronized List<Animal> findInactiveSince(Instant threshold) throws IOException {
        return load().values().stream()
            .filter(a -> a.lastSeen().isBefore(threshold))
            .toList();
    }

    @Override
    public synchronized List<Animal> findWithoutWaterVisitSince(Instant threshold) throws IOException {
        return load().values().stream()
            .filter(a -> a.lastWaterVisit() == null || a.lastWaterVisit().isBefore(threshold))
            .toList();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Map<String, Animal> load() throws IOException {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length == 0) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(mapper.readValue(bytes, MAP_TYPE));
    }

    private void persist(Map<String, Animal> data) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data);
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
