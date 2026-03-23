package io.cognis.vertical.sa.agriculture.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cognis.vertical.sa.agriculture.model.FarmerProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-backed {@link FarmerStore}.
 *
 * <p>Persists a {@code Map<String, FarmerProfile>} (phone → profile) to a single JSON file.
 * Writes are atomic (write-to-tmp then rename). Thread-safe via {@code synchronized}.
 */
public final class FileFarmerStore implements FarmerStore {

    private static final TypeReference<Map<String, FarmerProfile>> MAP_TYPE = new TypeReference<>() {};

    private final Path path;
    private final ObjectMapper mapper;

    public FileFarmerStore(Path path) {
        this.path   = path;
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public synchronized FarmerProfile findOrCreate(String phone) throws IOException {
        Map<String, FarmerProfile> all = load();
        if (all.containsKey(phone)) {
            return all.get(phone);
        }
        FarmerProfile created = FarmerProfile.create(phone);
        all.put(phone, created);
        persist(all);
        return created;
    }

    @Override
    public synchronized void save(FarmerProfile profile) throws IOException {
        Map<String, FarmerProfile> all = load();
        all.put(profile.phone(), profile);
        persist(all);
    }

    @Override
    public synchronized List<FarmerProfile> findByProvince(String province) throws IOException {
        if (province == null || province.isBlank()) {
            return List.of();
        }
        String normalized = province.trim().toLowerCase();
        return load().values().stream()
            .filter(p -> normalized.equals(p.province().toLowerCase()))
            .toList();
    }

    @Override
    public synchronized List<FarmerProfile> findAll() throws IOException {
        return List.copyOf(load().values());
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Map<String, FarmerProfile> load() throws IOException {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length == 0) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(mapper.readValue(bytes, MAP_TYPE));
    }

    private void persist(Map<String, FarmerProfile> data) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data);
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
