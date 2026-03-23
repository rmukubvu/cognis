package io.cognis.core.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends outbound WhatsApp messages directly via the Meta Cloud API.
 *
 * <p>This is the zero-Twilio-cost path — you pay only Meta's conversation-based pricing.
 * Requires a Meta Business account with WhatsApp Business API access and a
 * verified phone number.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * "whatsapp": {
 *   "provider":       "meta",
 *   "accessToken":    "EAAxxxxxxxxx...",   // Meta system user access token
 *   "phoneNumberId":  "123456789012345"    // from Meta Business Manager
 * }
 * }</pre>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>WhatsApp only — for SMS use {@link TwilioWhatsAppSender}</li>
 *   <li>24-hour messaging window: after 24h of inactivity the user must message first,
 *       or you must use an approved Message Template</li>
 * </ul>
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/messages">Meta Cloud API docs</a>
 */
public final class MetaCloudApiSender implements ChannelReplySender {

    private static final Logger LOG = LoggerFactory.getLogger(MetaCloudApiSender.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_URL = "https://graph.facebook.com/v18.0/%s/messages";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient http;
    private final String phoneNumberId;
    private final String bearerToken;

    /**
     * @param phoneNumberId  Meta phone number ID (from Business Manager → WhatsApp → Phone Numbers)
     * @param accessToken    Meta system user access token
     */
    public MetaCloudApiSender(String phoneNumberId, String accessToken) {
        this.phoneNumberId = phoneNumberId;
        this.bearerToken   = "Bearer " + accessToken;
        this.http          = new OkHttpClient();
    }

    @Override
    public boolean supports(String channel) {
        // Meta Cloud API is WhatsApp only; SMS requires a separate provider
        return "whatsapp".equalsIgnoreCase(channel);
    }

    @Override
    public void send(String toPhone, String message, String channel) throws IOException {
        if (!"whatsapp".equalsIgnoreCase(channel)) {
            throw new IOException("MetaCloudApiSender supports whatsapp only, got: " + channel);
        }
        if (toPhone == null || toPhone.isBlank()) {
            throw new IOException("toPhone must not be blank");
        }
        if (message == null || message.isBlank()) {
            LOG.debug("Skipping empty WhatsApp message to {}", toPhone);
            return;
        }

        // Meta expects E.164 without the leading '+' for the "to" field
        String normalizedPhone = toPhone.startsWith("+") ? toPhone.substring(1) : toPhone;

        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "to",                normalizedPhone,
            "type",              "text",
            "text",              Map.of("body", truncate(message, 4_096))
        );

        String json = MAPPER.writeValueAsString(payload);
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
            .url(API_URL.formatted(phoneNumberId))
            .addHeader("Authorization", bearerToken)
            .post(body)
            .build();

        LOG.info("Sending WhatsApp reply to {} via Meta Cloud API", toPhone);

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "(no body)";
                throw new IOException("Meta Cloud API returned HTTP %d: %s".formatted(response.code(), responseBody));
            }
            LOG.debug("Meta Cloud API WhatsApp delivered to {} (HTTP {})", toPhone, response.code());
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 1) + "…";
    }
}
