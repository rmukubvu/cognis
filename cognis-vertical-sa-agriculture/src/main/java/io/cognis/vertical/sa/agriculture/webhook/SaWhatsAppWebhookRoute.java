package io.cognis.vertical.sa.agriculture.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.sdk.RouteDefinition;
import io.cognis.sdk.RouteHandler;
import io.cognis.sdk.RouteResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
                handleInboundMessage(body, response);
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

    private void handleInboundMessage(InputStream body, RouteResponse response) {
        try {
            byte[] raw = body.readAllBytes();
            if (raw.length == 0) {
                response.status(200);
                response.json("{\"status\":\"empty\"}");
                return;
            }
            Map<String, Object> payload = JSON.readValue(raw, MAP_TYPE);
            List<ParsedMessage> messages = extractMessages(payload);
            for (ParsedMessage msg : messages) {
                LOG.info("Inbound SA WhatsApp from {} (len={})", msg.from(), msg.text().length());
                try {
                    messageHandler.accept(msg.from(), msg.text());
                } catch (Exception e) {
                    LOG.warn("SA WhatsApp handler threw for from={}", msg.from(), e);
                }
            }
            response.status(200);
            response.json("{\"status\":\"ok\",\"processed\":" + messages.size() + "}");
        } catch (Exception e) {
            LOG.warn("Failed to parse SA WhatsApp payload", e);
            response.status(400);
            response.json("{\"error\":\"invalid_payload\"}");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<ParsedMessage> extractMessages(Map<String, Object> payload) {
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
