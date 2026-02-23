package io.cognis.core.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FilePaymentStore implements PaymentStore {
    private final Path path;
    private final ObjectMapper mapper;

    public FilePaymentStore(Path path) {
        this.path = path;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public synchronized PaymentState load() throws IOException {
        if (!Files.exists(path)) {
            return PaymentState.empty();
        }
        try {
            return mapper.readValue(Files.readString(path), PaymentState.class);
        } catch (Exception ignored) {
            return PaymentState.empty();
        }
    }

    @Override
    public synchronized void save(PaymentState state) throws IOException {
        Files.createDirectories(path.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json + System.lineSeparator());
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
