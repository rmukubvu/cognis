package io.cognis.core.contact;

import io.cognis.core.model.ChatMessage;
import java.time.Instant;
import java.util.List;

/**
 * Represents a known field contact — a person who messages Cognis via any channel.
 *
 * <p>The phone number is the durable identity. A contact may switch from SMS to
 * WhatsApp and back; Cognis loads the same conversation history regardless of channel.
 *
 * <p>{@link #history()} stores the last N user/assistant turns so the agent has
 * conversation context across sessions without needing to replay the full transcript.
 */
public record Contact(
    String phone,
    String alias,
    String preferredChannel,
    Instant lastSeen,
    List<ChatMessage> history
) {
    public static Contact create(String phone) {
        return new Contact(phone, "", "sms", Instant.now(), List.of());
    }

    public Contact withHistory(List<ChatMessage> newHistory) {
        return new Contact(phone, alias, preferredChannel, lastSeen, List.copyOf(newHistory));
    }

    public Contact withAlias(String newAlias, String channel) {
        return new Contact(phone, newAlias, channel, Instant.now(), history);
    }

    public Contact withLastSeen(String channel) {
        return new Contact(phone, alias, channel, Instant.now(), history);
    }
}
