package io.cognis.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.model.ChatMessage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileConversationStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistConversationTurns() throws Exception {
        FileConversationStore store = new FileConversationStore(tempDir.resolve("memory/history.json"));
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
    }
}
