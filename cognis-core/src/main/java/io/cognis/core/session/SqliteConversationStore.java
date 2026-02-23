package io.cognis.core.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cognis.core.model.ChatMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SqliteConversationStore implements ConversationStore {
    private static final TypeReference<List<ChatMessage>> CHAT_MESSAGES = new TypeReference<>() {
    };

    private final String jdbcUrl;
    private final ObjectMapper mapper;

    public SqliteConversationStore(Path dbPath) throws IOException {
        if (dbPath == null) {
            throw new IllegalArgumentException("dbPath must not be null");
        }
        Files.createDirectories(dbPath.toAbsolutePath().getParent());
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        init();
    }

    @Override
    public synchronized void append(ConversationTurn turn) throws IOException {
        String sql = """
            INSERT INTO conversation_turns (id, created_at, prompt, response, transcript_json)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, turn.createdAt().toString());
            statement.setString(3, safe(turn.prompt()));
            statement.setString(4, safe(turn.response()));
            statement.setString(5, mapper.writeValueAsString(turn.transcript()));
            statement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            throw new IOException("Failed to append conversation turn", e);
        }
    }

    @Override
    public synchronized List<ConversationTurn> list() throws IOException {
        String sql = """
            SELECT created_at, prompt, response, transcript_json
            FROM conversation_turns
            ORDER BY created_at ASC
            """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<ConversationTurn> turns = new ArrayList<>();
            while (resultSet.next()) {
                Instant createdAt = Instant.parse(resultSet.getString("created_at"));
                String prompt = resultSet.getString("prompt");
                String response = resultSet.getString("response");
                List<ChatMessage> transcript = mapper.readValue(
                    resultSet.getString("transcript_json"),
                    CHAT_MESSAGES
                );
                turns.add(new ConversationTurn(createdAt, prompt, response, transcript));
            }
            return turns;
        } catch (SQLException e) {
            throw new IOException("Failed to list conversation turns", e);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL;");
            statement.execute("PRAGMA synchronous=NORMAL;");
        }
        return connection;
    }

    private void init() throws IOException {
        String ddl = """
            CREATE TABLE IF NOT EXISTS conversation_turns (
                id TEXT PRIMARY KEY,
                created_at TEXT NOT NULL,
                prompt TEXT NOT NULL,
                response TEXT NOT NULL,
                transcript_json TEXT NOT NULL
            )
            """;
        String idx = """
            CREATE INDEX IF NOT EXISTS idx_conversation_turns_created_at
            ON conversation_turns(created_at DESC)
            """;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
            statement.execute(idx);
        } catch (SQLException e) {
            throw new IOException("Failed to initialize SQLite conversation store", e);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
