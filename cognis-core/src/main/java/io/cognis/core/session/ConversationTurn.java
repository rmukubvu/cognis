package io.cognis.core.session;

import io.cognis.core.model.ChatMessage;
import java.time.Instant;
import java.util.List;

public record ConversationTurn(
    Instant createdAt,
    String prompt,
    String response,
    List<ChatMessage> transcript
) {
}
