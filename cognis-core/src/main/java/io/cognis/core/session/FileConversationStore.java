package io.cognis.core.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FileConversationStore implements ConversationStore {
    private final Path path;
    private final ObjectMapper mapper;

    public FileConversationStore(Path path) {
        this.path = path;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public synchronized void append(ConversationTurn turn) throws IOException {
        List<ConversationTurn> existing = new ArrayList<>(list());
        existing.add(turn);
        save(existing);
    }

    @Override
    public synchronized List<ConversationTurn> list() throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        return mapper.readValue(Files.readString(path), new TypeReference<List<ConversationTurn>>() {
        });
    }

    private void save(List<ConversationTurn> turns) throws IOException {
        Files.createDirectories(path.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(turns);
        Files.writeString(path, json + System.lineSeparator());
    }
}
