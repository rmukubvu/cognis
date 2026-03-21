package io.cognis.vertical.humanitarian.webhook;

import io.cognis.sdk.RouteDefinition;
import io.cognis.sdk.RouteHandler;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * HTTP route that receives inbound SMS payloads (Twilio or Africa's Talking format)
 * and hands them off to a configurable message handler.
 *
 * <p>The handler receives the sender phone number and message body. It is the
 * caller's responsibility to route those to the agent orchestrator or any other
 * downstream system.
 *
 * <p>Responds with a TwiML-compatible plain-text ACK (status 200) so that the SMS
 * gateway does not retry the webhook.
 */
public final class SmsWebhookRoute implements RouteDefinition {

    private final BiConsumer<String, String> messageHandler;

    /**
     * @param messageHandler called with {@code (senderPhone, messageBody)} for each inbound SMS
     */
    public SmsWebhookRoute(BiConsumer<String, String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public String method() {
        return "POST";
    }

    @Override
    public String path() {
        return "/webhook/sms";
    }

    @Override
    public RouteHandler handler() {
        return this::handle;
    }

    private void handle(String method, String path, Map<String, String> headers,
                        InputStream body, io.cognis.sdk.RouteResponse response) throws Exception {
        byte[] bytes = body.readAllBytes();
        String rawBody = new String(bytes, StandardCharsets.UTF_8);

        Map<String, String> params = parseFormEncoded(rawBody);

        // Support both Twilio (From / Body) and Africa's Talking (from / text) field names
        String from = params.getOrDefault("From", params.getOrDefault("from", ""));
        String text = params.getOrDefault("Body", params.getOrDefault("text", rawBody));

        if (!from.isBlank() && !text.isBlank()) {
            try {
                messageHandler.accept(from, text);
            } catch (Exception e) {
                // Log and continue — never fail the webhook ACK
            }
        }

        response.status(200);
        response.header("Content-Type", "text/plain; charset=utf-8");
        response.body("OK".getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseFormEncoded(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return params;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }
}
