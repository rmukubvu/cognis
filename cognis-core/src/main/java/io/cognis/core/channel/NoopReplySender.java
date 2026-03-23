package io.cognis.core.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op {@link ChannelReplySender} — logs the outbound message instead of sending it.
 *
 * <p>Used when no WhatsApp/SMS provider is configured. Lets the system run end-to-end
 * in development without a Twilio or Meta account.
 */
public final class NoopReplySender implements ChannelReplySender {

    private static final Logger LOG = LoggerFactory.getLogger(NoopReplySender.class);

    @Override
    public void send(String toPhone, String message, String channel) {
        LOG.info("[NOOP] Would send via {} to {}: {}", channel, toPhone,
            message.length() > 120 ? message.substring(0, 120) + "…" : message);
    }

    @Override
    public boolean supports(String channel) {
        return true; // accepts any channel — just logs
    }
}
