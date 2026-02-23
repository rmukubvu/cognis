package io.cognis.core.cron;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FileCronStore implements CronStore {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileCronStore(Path path) {
        this.path = path;
    }

    @Override
    public List<CronJob> load() throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        return mapper.readValue(Files.readString(path), new TypeReference<List<CronJob>>() {
        });
    }

    @Override
    public void save(List<CronJob> jobs) throws IOException {
        Files.createDirectories(path.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobs);
        Files.writeString(path, json + System.lineSeparator());
    }
}
