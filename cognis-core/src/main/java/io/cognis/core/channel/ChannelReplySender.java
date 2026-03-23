package io.cognis.core.channel;

import java.io.IOException;

/**
 * Sends outbound messages back to a user via a specific messaging channel.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link TwilioWhatsAppSender}  — WhatsApp and SMS via Twilio REST API</li>
 *   <li>{@link MetaCloudApiSender}    — WhatsApp via Meta Cloud API (no Twilio cost)</li>
 *   <li>{@link NoopReplySender}       — logs only; used in dev/test environments</li>
 * </ul>
 *
 * <p>Verticals inject this via {@code ToolContext.service("replySender", ChannelReplySender.class)}
 * and call it after the agent produces a response.
 */
public interface ChannelReplySender {

    /**
     * Sends {@code message} to the given phone number via the given channel.
     *
     * @param toPhone  recipient phone number in E.164 format (e.g. {@code +27821234567})
     * @param message  message body text — keep under 1,600 chars for WhatsApp
     * @param channel  {@code "whatsapp"} or {@code "sms"}
     * @throws IOException if the underlying transport fails after retries
     */
    void send(String toPhone, String message, String channel) throws IOException;

    /**
     * Returns {@code true} if this sender can deliver via the given channel.
     * Verticals should call this before {@link #send} to decide whether to attempt delivery.
     */
    boolean supports(String channel);
}
