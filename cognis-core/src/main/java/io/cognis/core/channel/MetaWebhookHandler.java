package io.cognis.core.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inbound webhook handler for the Meta Cloud API (WhatsApp Business).
 *
 * <p>Registers two Undertow routes via {@link io.cognis.core.api.GatewayServer#registerRoute}:
 * <ul>
 *   <li>{@code GET  /webhook/meta} — responds to Meta's subscription verification challenge</li>
 *   <li>{@code POST /webhook/meta} — processes inbound messages and media events</li>
 * </ul>
 *
 * <h2>What it handles</h2>
 * <ul>
 *   <li>Text messages → run through AgentOrchestrator → reply via {@link MetaCloudApiSender}</li>
 *   <li>Audio messages → transcription stub (wire {@code Transcriber} to enable voice)</li>
 *   <li>Image messages → logged; ignored (extend {@code handleMedia} for vision support)</li>
 *   <li>Read receipts and status updates → acknowledged with 200, silently dropped</li>
 * </ul>
 *
 * <h2>Security</h2>
 * <p>When {@code appSecret} is set, every POST is verified against the
 * {@code X-Hub-Signature-256} header using HMAC-SHA256. Requests with invalid
 * or missing signatures are rejected with HTTP 403 before any payload parsing.
 * Set appSecret in cognis.json as {@code "appSecret": "your_meta_app_secret"}.
 *
 * <h2>Setup</h2>
 * <pre>{@code
 * "whatsapp": {
 *   "provider":       "meta",
 *   "accessToken":    "EAAxxxxxxxxx...",
 *   "phoneNumberId":  "123456789012345",
 *   "verifyToken":    "my_random_verify_token",   // set in Meta webhook config
 *   "appSecret":      "abc123..."                 // Meta app secret for HMAC validation
 * }
 * }</pre>
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks">Meta Webhook docs</a>
 */
public final class MetaWebhookHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MetaWebhookHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String verifyToken;
    private final String appSecret;
    private final AgentOrchestrator orchestrator;
    private final AgentSettings agentSettings;
    private final ChannelReplySender replySender;
    private final Path workspace;
    private final ExecutorService executor;

    /**
     * @param verifyToken   token you configured in Meta's webhook settings (matches hub.verify_token)
     * @param appSecret     Meta app secret used to validate HMAC-SHA256 signatures (may be null to skip)
     * @param orchestrator  Cognis agent to process each inbound message
     * @param agentSettings model + system prompt settings for the orchestrator run
     * @param replySender   outbound sender (typically {@link MetaCloudApiSender})
     * @param workspace     agent workspace path
     */
    public MetaWebhookHandler(
        String verifyToken,
        String appSecret,
        AgentOrchestrator orchestrator,
        AgentSettings agentSettings,
        ChannelReplySender replySender,
        Path workspace
    ) {
        this.verifyToken   = verifyToken == null ? "" : verifyToken;
        this.appSecret     = appSecret   == null ? "" : appSecret;
        this.orchestrator  = orchestrator;
        this.agentSettings = agentSettings;
        this.replySender   = replySender;
        this.workspace     = workspace;
        this.executor      = Executors.newVirtualThreadPerTaskExecutor();
    }

    // -------------------------------------------------------------------------
    // GET /webhook/meta  — Meta subscription verification
    // -------------------------------------------------------------------------

    /**
     * Returns an Undertow handler for {@code GET /webhook/meta}.
     *
     * <p>Meta sends: {@code ?hub.mode=subscribe&hub.verify_token=X&hub.challenge=Y}
     * We must echo {@code hub.challenge} with 200 if hub.verify_token matches.
     */
    public HttpHandler verificationHandler() {
        return exchange -> {
            Deque<String> modeQ      = exchange.getQueryParameters().get("hub.mode");
            Deque<String> tokenQ     = exchange.getQueryParameters().get("hub.verify_token");
            Deque<String> challengeQ = exchange.getQueryParameters().get("hub.challenge");

            String mode      = modeQ      != null ? modeQ.peekFirst()      : "";
            String token     = tokenQ     != null ? tokenQ.peekFirst()      : "";
            String challenge = challengeQ != null ? challengeQ.peekFirst()  : "";

            if ("subscribe".equals(mode) && verifyToken.equals(token)) {
                LOG.info("Meta webhook verification success");
                exchange.setStatusCode(200);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.getResponseSender().send(challenge);
            } else {
                LOG.warn("Meta webhook verification failed — token mismatch (got: {})", token);
                exchange.setStatusCode(403);
                exchange.getResponseSender().send("Forbidden");
            }
        };
    }

    // -------------------------------------------------------------------------
    // POST /webhook/meta  — inbound message events
    // -------------------------------------------------------------------------

    /**
     * Returns an Undertow handler for {@code POST /webhook/meta}.
     *
     * <p>Messages are processed asynchronously so Meta's 20-second timeout is
     * never at risk. We always return 200 immediately after signature validation.
     */
    public HttpHandler messageHandler() {
        return exchange -> {
            exchange.getRequestReceiver().receiveFullBytes((ex, bytes) -> {
                String raw = new String(bytes, StandardCharsets.UTF_8);

                // Validate HMAC signature if appSecret is configured
                if (!appSecret.isBlank()) {
                    String sig = exchange.getRequestHeaders().getFirst("X-Hub-Signature-256");
                    if (!validateSignature(raw, sig)) {
                        LOG.warn("Meta webhook signature validation failed");
                        ex.setStatusCode(403);
                        ex.getResponseSender().send("Forbidden");
                        return;
                    }
                }

                // Acknowledge immediately — Meta requires a fast 200
                ex.setStatusCode(200);
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                ex.getResponseSender().send("{\"status\":\"ok\"}");

                // Process asynchronously so we never block the I/O thread
                executor.submit(() -> processPayload(raw));
            });
        };
    }

    // -------------------------------------------------------------------------
    // Payload processing
    // -------------------------------------------------------------------------

    private void processPayload(String raw) {
        try {
            JsonNode root = MAPPER.readTree(raw);

            // Only handle whatsapp_business_account events
            if (!"whatsapp_business_account".equals(root.path("object").asText())) {
                return;
            }

            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    if (!"messages".equals(change.path("field").asText())) continue;
                    JsonNode value = change.path("value");

                    for (JsonNode msg : value.path("messages")) {
                        handleMessage(msg, value.path("contacts"));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error processing Meta webhook payload", e);
        }
    }

    private void handleMessage(JsonNode msg, JsonNode contacts) {
        String type = msg.path("type").asText();
        String from = msg.path("from").asText(); // E.164 without + (Meta format)
        String toPhone = from.startsWith("+") ? from : "+" + from;

        String senderName = "User";
        for (JsonNode contact : contacts) {
            String waId = contact.path("wa_id").asText();
            if (from.equals(waId) || toPhone.equals("+" + waId)) {
                senderName = contact.path("profile").path("name").asText(senderName);
                break;
            }
        }

        String messageText;
        switch (type) {
            case "text" -> {
                messageText = msg.path("text").path("body").asText("").trim();
                if (messageText.isBlank()) {
                    LOG.debug("Ignoring empty text message from {}", toPhone);
                    return;
                }
            }
            case "audio", "voice" -> {
                // Voice messages: transcription not yet wired — nudge user
                LOG.info("Voice message from {} — transcription not configured", toPhone);
                sendReply(toPhone, "I received your voice message but can't transcribe it yet. Please send text instead.");
                return;
            }
            case "image", "video", "document" -> {
                LOG.debug("Media message ({}) from {} — not processed", type, toPhone);
                return;
            }
            default -> {
                // Status updates, read receipts, reactions — ignore silently
                LOG.debug("Ignoring Meta event type: {}", type);
                return;
            }
        }

        LOG.info("Inbound WhatsApp from={} name={} text={}", toPhone, senderName, messageText);

        // Build a context-aware prompt so the agent knows the caller's identity
        String prompt = buildPrompt(senderName, toPhone, messageText);

        try {
            var result = orchestrator.run(prompt, agentSettings, workspace);
            String reply = result.content();
            if (reply == null || reply.isBlank()) {
                reply = "I'm on it — I'll get back to you shortly.";
            }
            sendReply(toPhone, reply);
        } catch (Exception e) {
            LOG.error("Agent error processing message from {}: {}", toPhone, e.getMessage(), e);
            sendReply(toPhone, "Sorry, I ran into an error. Please try again in a moment.");
        }
    }

    private void sendReply(String toPhone, String message) {
        try {
            replySender.send(toPhone, message, "whatsapp");
            LOG.info("WhatsApp reply sent to {}", toPhone);
        } catch (IOException e) {
            LOG.error("Failed to send WhatsApp reply to {}: {}", toPhone, e.getMessage(), e);
        }
    }

    private String buildPrompt(String senderName, String phone, String message) {
        return """
            WhatsApp message from %s (%s):

            %s

            Respond naturally and concisely — this will be delivered as a WhatsApp message.
            Avoid markdown formatting (no headers, no bullet symbols) unless the user asked for a list.
            Keep responses under 1,500 characters when possible.
            """.formatted(senderName, phone, message).strip();
    }

    // -------------------------------------------------------------------------
    // HMAC-SHA256 signature validation
    // -------------------------------------------------------------------------

    /**
     * Validates the {@code X-Hub-Signature-256} header against the raw payload.
     *
     * <p>Meta sends: {@code X-Hub-Signature-256: sha256=<hex_digest>}
     * We compute HMAC-SHA256(appSecret, rawBody) and compare in constant time.
     */
    private boolean validateSignature(String rawBody, String header) {
        if (header == null || !header.startsWith("sha256=")) {
            return false;
        }
        String theirHex = header.substring("sha256=".length());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));

            String ourHex = bytesToHex(computed);
            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(ourHex, theirHex);
        } catch (Exception e) {
            LOG.error("HMAC validation error", e);
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
