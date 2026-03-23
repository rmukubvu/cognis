package io.cognis.vertical.sa.agriculture.webhook;

import io.cognis.sdk.RouteDefinition;
import io.cognis.sdk.RouteHandler;
import io.cognis.sdk.RouteResponse;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles inbound SMS webhooks on {@code /webhook/sa/sms}.
 *
 * <p>Compatible with Twilio and Africa's Talking URL-encoded POST format:
 * {@code From=+27821234567&Body=I+have+2+acres+of+maize}.
 */
public final class SaSmsWebhookRoute implements RouteDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(SaSmsWebhookRoute.class);
    public static final String PATH = "/webhook/sa/sms";

    private final BiConsumer<String, String> messageHandler;

    public SaSmsWebhookRoute(BiConsumer<String, String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override public String method() { return "POST"; }
    @Override public String path()   { return PATH; }

    @Override
    public RouteHandler handler() {
        return (method, path, headers, body, response) -> {
            if (!"POST".equalsIgnoreCase(method)) {
                response.status(405);
                response.json("{\"error\":\"method_not_allowed\"}");
                return;
            }
            handleInboundSms(body, response);
        };
    }

    private void handleInboundSms(InputStream body, RouteResponse response) {
        try {
            byte[] raw = body.readAllBytes();
            if (raw.length == 0) {
                response.status(200);
                response.json("{\"status\":\"empty\"}");
                return;
            }
            Map<String, String> params = parseFormEncoded(new String(raw, StandardCharsets.UTF_8));
            String from = params.getOrDefault("From", params.getOrDefault("from", ""));
            String text = params.getOrDefault("Body", params.getOrDefault("body",
                          params.getOrDefault("text", "")));

            if (from.isBlank() || text.isBlank()) {
                LOG.warn("SA SMS webhook: missing From or Body in payload");
                response.status(400);
                response.json("{\"error\":\"missing_from_or_body\"}");
                return;
            }

            LOG.info("Inbound SA SMS from {} (len={})", from, text.length());
            try {
                messageHandler.accept(from, text);
            } catch (Exception e) {
                LOG.warn("SA SMS handler threw for from={}", from, e);
            }
            response.status(200);
            response.json("{\"status\":\"ok\"}");
        } catch (Exception e) {
            LOG.warn("Failed to parse SA SMS payload", e);
            response.status(400);
            response.json("{\"error\":\"invalid_payload\"}");
        }
    }

    private static Map<String, String> parseFormEncoded(String body) {
        Map<String, String> params = new HashMap<>();
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, val);
            }
        }
        return params;
    }
}
