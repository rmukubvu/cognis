package io.cognis.core.channel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends outbound WhatsApp and SMS messages via the Twilio REST API.
 *
 * <h2>WhatsApp</h2>
 * Uses the {@code whatsapp:} prefix on From/To numbers. Requires a Twilio account
 * with a WhatsApp-enabled number (sandbox or production Business API approval).
 *
 * <h2>SMS</h2>
 * Uses plain E.164 phone numbers. The same Twilio account and auth credentials work
 * for both channels — only the From number and prefixes differ.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * "whatsapp": {
 *   "provider":    "twilio",
 *   "accountSid":  "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
 *   "authToken":   "your_auth_token",
 *   "fromNumber":  "+14155238886"   // your Twilio number, no whatsapp: prefix
 * }
 * }</pre>
 *
 * @see <a href="https://www.twilio.com/docs/whatsapp/api">Twilio WhatsApp API docs</a>
 */
public final class TwilioWhatsAppSender implements ChannelReplySender {

    private static final Logger LOG = LoggerFactory.getLogger(TwilioWhatsAppSender.class);
    private static final Set<String> SUPPORTED = Set.of("whatsapp", "sms");
    private static final String BASE_URL = "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final OkHttpClient http;
    private final String accountSid;
    private final String authHeader;
    private final String fromNumber;

    /**
     * @param accountSid  Twilio Account SID (starts with "AC")
     * @param authToken   Twilio Auth Token
     * @param fromNumber  your Twilio phone number in E.164 format (e.g. {@code +14155238886})
     */
    public TwilioWhatsAppSender(String accountSid, String authToken, String fromNumber) {
        this.accountSid  = accountSid;
        this.fromNumber  = fromNumber;
        this.authHeader  = "Basic " + Base64.getEncoder().encodeToString(
            (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        this.http = new OkHttpClient();
    }

    @Override
    public boolean supports(String channel) {
        return channel != null && SUPPORTED.contains(channel.toLowerCase());
    }

    @Override
    public void send(String toPhone, String message, String channel) throws IOException {
        if (toPhone == null || toPhone.isBlank()) {
            throw new IOException("toPhone must not be blank");
        }
        if (message == null || message.isBlank()) {
            LOG.debug("Skipping empty message to {}", toPhone);
            return;
        }

        boolean isWhatsApp = "whatsapp".equalsIgnoreCase(channel);
        String from = isWhatsApp ? "whatsapp:" + fromNumber : fromNumber;
        String to   = isWhatsApp ? "whatsapp:" + toPhone    : toPhone;

        FormBody body = new FormBody.Builder()
            .add("From", from)
            .add("To",   to)
            .add("Body", truncate(message, 1_600))
            .build();

        Request request = new Request.Builder()
            .url(BASE_URL.formatted(accountSid))
            .addHeader("Authorization", authHeader)
            .post(body)
            .build();

        LOG.info("Sending {} reply to {} via Twilio", channel, toPhone);

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "(no body)";
                throw new IOException("Twilio returned HTTP %d: %s".formatted(response.code(), responseBody));
            }
            LOG.debug("Twilio {} reply delivered to {} (HTTP {})", channel, toPhone, response.code());
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 1) + "…";
    }
}
