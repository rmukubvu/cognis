package io.cognis.vertical.sa.agriculture.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.sdk.RouteDefinition;
import io.cognis.sdk.RouteHandler;
import io.cognis.sdk.RouteResponse;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Meta WhatsApp Business API webhooks on {@code /webhook/sa/whatsapp}.
 *
 * <p>Dedicated route for the SA Agriculture vertical — runs on a separate path
 * so it can coexist with the Humanitarian vertical's {@code /webhook/whatsapp}.
 */
public final class SaWhatsAppWebhookRoute implements RouteDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(SaWhatsAppWebhookRoute.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String PATH = "/webhook/sa/whatsapp";

    private final BiConsumer<String, String> messageHandler;
    private final String verifyToken;

    public SaWhatsAppWebhookRoute(BiConsumer<String, String> messageHandler) {
        this(messageHandler, System.getenv().getOrDefault("WHATSAPP_VERIFY_TOKEN", "cognis"));
    }

    SaWhatsAppWebhookRoute(BiConsumer<String, String> messageHandler, String verifyToken) {
        this.messageHandler = messageHandler;
        this.verifyToken    = verifyToken;
    }

    @Override public String method() { return "GET"; }
    @Override public String path()   { return PATH; }

    @Override
    public RouteHandler handler() {
        return (method, path, headers, body, response) -> {
            if ("GET".equalsIgnoreCase(method)) {
                handleVerification(headers, response);
            } else if ("POST".equalsIgnoreCase(method)) {
                String contentType = headers.getOrDefault("content-type", "");
                if (contentType.contains("application/x-www-form-urlencoded")) {
                    handleTwilioMessage(body, response);
                } else {
                    handleMetaMessage(body, response);
                }
            } else {
                response.status(405);
                response.json("{\"error\":\"method_not_allowed\"}");
            }
        };
    }

    private void handleVerification(Map<String, String> headers, RouteResponse response) {
        String challenge = headers.getOrDefault("hub.challenge", "");
        String token     = headers.getOrDefault("hub.verify_token", "");
        if (challenge.isBlank() || token.isBlank()) {
            LOG.warn("SA WhatsApp verification called but hub params not in headers");
            response.status(200);
            response.body(new byte[0]);
            return;
        }
        if (!verifyToken.equals(token)) {
            LOG.warn("SA WhatsApp verification failed: token mismatch");
            response.status(403);
            response.json("{\"error\":\"verify_token_mismatch\"}");
            return;
        }
        LOG.info("SA WhatsApp webhook verified");
        response.status(200);
        response.body(challenge.getBytes(StandardCharsets.UTF_8));
    }

    /** Twilio: {@code application/x-www-form-urlencoded} — fields: From, Body */
    private void handleTwilioMessage(InputStream body, RouteResponse response) {
        try {
            String raw = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> fields = parseFormEncoded(raw);
            String from = fields.getOrDefault("From", "");
            String text = fields.getOrDefault("Body", "");
            // Twilio prefixes WhatsApp numbers: "whatsapp:+27821234567" → strip prefix
            if (from.startsWith("whatsapp:")) {
                from = from.substring("whatsapp:".length());
            }
            if (from.isBlank() || text.isBlank()) {
                response.status(200);
                response.json("{\"status\":\"empty\"}");
                return;
            }
            LOG.info("Inbound SA WhatsApp (Twilio) from {} (len={})", from, text.length());
            // ACK Twilio immediately — LLM reply is sent back via TwilioWhatsAppSender,
            // not via this HTTP response. Processing async prevents connection-reset on
            // slow LLM calls that exceed Twilio's 15 s webhook timeout.
            response.status(200);
            response.json("{\"status\":\"ok\",\"processed\":1}");
            final String finalFrom = from;
            final String finalText = text;
            Thread.ofVirtual().start(() -> dispatchOne(finalFrom, finalText));
        } catch (Exception e) {
            LOG.warn("Failed to parse Twilio WhatsApp payload", e);
            response.status(400);
            response.json("{\"error\":\"invalid_payload\"}");
        }
    }

    /** Meta Cloud API: {@code application/json} — nested entry/changes/messages */
    private void handleMetaMessage(InputStream body, RouteResponse response) {
        try {
            byte[] raw = body.readAllBytes();
            if (raw.length == 0) {
                response.status(200);
                response.json("{\"status\":\"empty\"}");
                return;
            }
            Map<String, Object> payload = JSON.readValue(raw, MAP_TYPE);
            List<ParsedMessage> messages = extractMetaMessages(payload);
            // ACK Meta immediately — same async pattern as Twilio
            response.status(200);
            response.json("{\"status\":\"ok\",\"processed\":" + messages.size() + "}");
            messages.forEach(msg -> {
                LOG.info("Inbound SA WhatsApp (Meta) from {} (len={})", msg.from(), msg.text().length());
                Thread.ofVirtual().start(() -> dispatchOne(msg.from(), msg.text()));
            });
        } catch (Exception e) {
            LOG.warn("Failed to parse Meta WhatsApp payload", e);
            response.status(400);
            response.json("{\"error\":\"invalid_payload\"}");
        }
    }

    private void dispatchOne(String from, String text) {
        try {
            messageHandler.accept(from, text);
        } catch (Exception e) {
            LOG.warn("SA WhatsApp handler threw for from={}", from, e);
        }
    }

    private static Map<String, String> parseFormEncoded(String body) {
        Map<String, String> result = new HashMap<>();
        if (body == null || body.isBlank()) return result;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 1) continue;
            String key   = URLDecoder.decode(pair.substring(0, eq),  StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<ParsedMessage> extractMetaMessages(Map<String, Object> payload) {
        List<ParsedMessage> result = new ArrayList<>();
        List<Object> entries = (List<Object>) payload.get("entry");
        if (entries == null) return result;
        for (Object entry : entries) {
            Map<Object, Object> entryMap = (Map) entry;
            List<Object> changes = (List<Object>) entryMap.get("changes");
            if (changes == null) continue;
            for (Object change : changes) {
                Map<Object, Object> changeMap = (Map) change;
                Map<Object, Object> value = (Map) changeMap.get("value");
                if (value == null) continue;
                List<Object> msgs = (List<Object>) value.get("messages");
                if (msgs == null) continue;
                for (Object msg : msgs) {
                    Map<Object, Object> msgMap = (Map) msg;
                    Object typeObj = msgMap.getOrDefault("type", "");
                    if (!"text".equals(typeObj == null ? "" : typeObj.toString())) continue;
                    Object fromObj = msgMap.getOrDefault("from", "");
                    String from = fromObj == null ? "" : fromObj.toString();
                    Map<Object, Object> textObj = (Map) msgMap.get("text");
                    if (textObj == null) continue;
                    Object bodyObj = textObj.getOrDefault("body", "");
                    String text = bodyObj == null ? "" : bodyObj.toString();
                    if (!from.isBlank() && !text.isBlank()) {
                        result.add(new ParsedMessage(from, text));
                    }
                }
            }
        }
        return result;
    }

    private record ParsedMessage(String from, String text) {}
}
