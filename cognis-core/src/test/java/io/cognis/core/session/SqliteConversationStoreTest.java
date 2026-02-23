package io.cognis.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.model.ChatMessage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteConversationStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistConversationTurns() throws Exception {
        SqliteConversationStore store = new SqliteConversationStore(tempDir.resolve("memory/cognis.db"));
        ConversationTurn turn = new ConversationTurn(
            Instant.parse("2026-01-01T00:00:00Z"),
            "hello",
            "hi",
            List.of(ChatMessage.user("hello"), ChatMessage.assistant("hi"))
        );

        store.append(turn);

        List<ConversationTurn> saved = store.list();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().prompt()).isEqualTo("hello");
        assertThat(saved.getFirst().response()).isEqualTo("hi");
        assertThat(saved.getFirst().transcript()).hasSize(2);
    }

    @Test
    void shouldReturnTurnsSortedByTimestampAscending() throws Exception {
        SqliteConversationStore store = new SqliteConversationStore(tempDir.resolve("memory/cognis.db"));
        store.append(new ConversationTurn(
            Instant.parse("2026-01-02T00:00:00Z"),
            "second",
            "second-reply",
            List.of(ChatMessage.user("second"))
        ));
        store.append(new ConversationTurn(
            Instant.parse("2026-01-01T00:00:00Z"),
            "first",
            "first-reply",
            List.of(ChatMessage.user("first"))
        ));

        List<ConversationTurn> turns = store.list();
        assertThat(turns).hasSize(2);
        assertThat(turns.get(0).prompt()).isEqualTo("first");
        assertThat(turns.get(1).prompt()).isEqualTo("second");
    }

    @Test
    void shouldReturnEmptyListForFreshDatabase() throws Exception {
        SqliteConversationStore store = new SqliteConversationStore(tempDir.resolve("memory/cognis.db"));
        assertThat(store.list()).isEmpty();
    }
}
