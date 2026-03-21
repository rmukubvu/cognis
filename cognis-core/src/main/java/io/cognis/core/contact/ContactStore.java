package io.cognis.core.contact;

import io.cognis.core.model.ChatMessage;
import java.io.IOException;
import java.util.List;

/**
 * Persistent registry of field contacts, keyed by phone number.
 *
 * <p>A contact's identity spans channels: the same phone number messaging via SMS
 * and WhatsApp loads the same history. This is the cross-channel identity layer
 * that prevents field officers from losing conversation context when they switch channels.
 */
public interface ContactStore {

    /**
     * Return an existing contact or create a new one for the given phone number.
     * Never returns null.
     */
    Contact findOrCreate(String phone) throws IOException;

    /**
     * Append one user message and one assistant response to a contact's history.
     * Automatically trims history to the last {@code maxHistory} turns.
     *
     * @param phone     the contact's phone number
     * @param user      the field officer's inbound message
     * @param assistant the agent's response
     * @param maxHistory maximum number of turns (user+assistant pairs) to retain
     */
    void appendTurn(String phone, ChatMessage user, ChatMessage assistant, int maxHistory) throws IOException;

    /**
     * Return the most recent {@code maxTurns} user+assistant message pairs as a flat list,
     * oldest first. Returns an empty list if the contact has no history.
     */
    List<ChatMessage> recentHistory(String phone, int maxTurns) throws IOException;

    /**
     * Update the display alias and preferred channel for a contact.
     * Used when a field officer introduces themselves ("My name is Amara, calling from Gulu").
     */
    void updateAlias(String phone, String alias, String channel) throws IOException;
}
