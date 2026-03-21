package io.cognis.core.contact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cognis.core.model.ChatMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-backed {@link ContactStore}.
 *
 * <p>Persists a {@code Map<String, Contact>} (phone → contact) to a single JSON file.
 * Writes are atomic (write-to-tmp then rename). Thread-safe via {@code synchronized}.
 */
public final class FileContactStore implements ContactStore {

    private static final TypeReference<Map<String, Contact>> MAP_TYPE = new TypeReference<>() {};

    private final Path path;
    private final ObjectMapper mapper;

    public FileContactStore(Path path) {
        this.path = path;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public synchronized Contact findOrCreate(String phone) throws IOException {
        Map<String, Contact> contacts = load();
        return contacts.computeIfAbsent(phone, Contact::create);
    }

    @Override
    public synchronized void appendTurn(
        String phone,
        ChatMessage user,
        ChatMessage assistant,
        int maxHistory
    ) throws IOException {
        Map<String, Contact> contacts = load();
        Contact existing = contacts.getOrDefault(phone, Contact.create(phone));

        List<ChatMessage> history = new ArrayList<>(existing.history());
        history.add(user);
        history.add(assistant);

        // Trim to maxHistory turns (each turn = 2 messages: user + assistant)
        int maxMessages = maxHistory * 2;
        if (history.size() > maxMessages) {
            history = history.subList(history.size() - maxMessages, history.size());
        }

        contacts.put(phone, existing.withHistory(history).withLastSeen(existing.preferredChannel()));
        save(contacts);
    }

    @Override
    public synchronized List<ChatMessage> recentHistory(String phone, int maxTurns) throws IOException {
        Map<String, Contact> contacts = load();
        Contact contact = contacts.get(phone);
        if (contact == null || contact.history().isEmpty()) {
            return List.of();
        }
        List<ChatMessage> history = contact.history();
        int maxMessages = maxTurns * 2;
        if (history.size() <= maxMessages) {
            return List.copyOf(history);
        }
        return List.copyOf(history.subList(history.size() - maxMessages, history.size()));
    }

    @Override
    public synchronized void updateAlias(String phone, String alias, String channel) throws IOException {
        Map<String, Contact> contacts = load();
        Contact existing = contacts.getOrDefault(phone, Contact.create(phone));
        contacts.put(phone, existing.withAlias(alias, channel));
        save(contacts);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private Map<String, Contact> load() throws IOException {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(mapper.readValue(Files.readString(path), MAP_TYPE));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void save(Map<String, Contact> contacts) throws IOException {
        Files.createDirectories(path.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(contacts);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json + System.lineSeparator());
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
