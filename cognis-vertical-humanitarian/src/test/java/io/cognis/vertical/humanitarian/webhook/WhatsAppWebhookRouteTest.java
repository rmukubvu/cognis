package io.cognis.vertical.humanitarian.webhook;

import io.cognis.sdk.RouteHandler;
import io.cognis.sdk.RouteResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppWebhookRouteTest {

    private static final String VERIFY_TOKEN = "test-token-123";

    // ── RouteDefinition contract ─────────────────────────────────────────────

    @Test
    void pathIsWebhookWhatsapp() {
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute((f, t) -> {}, VERIFY_TOKEN);
        assertThat(route.path()).isEqualTo("/webhook/whatsapp");
    }

    @Test
    void methodIsGet() {
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute((f, t) -> {}, VERIFY_TOKEN);
        assertThat(route.method()).isEqualTo("GET");
    }

    @Test
    void handlerIsNotNull() {
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute((f, t) -> {}, VERIFY_TOKEN);
        assertThat(route.handler()).isNotNull();
    }

    // ── Verification challenge ────────────────────────────────────────────────

    @Test
    void verificationWithCorrectTokenResponds200() throws Exception {
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute((f, t) -> {}, VERIFY_TOKEN);
        CaptureResponse resp = new CaptureResponse();
        Map<String, String> headers = Map.of(
            "hub.mode", "subscribe",
            "hub.challenge", "challenge-abc",
            "hub.verify_token", VERIFY_TOKEN
        );
        route.handler().handle("GET", "/webhook/whatsapp", headers, emptyBody(), resp);
        assertThat(resp.status).isEqualTo(200);
        assertThat(new String(resp.body, StandardCharsets.UTF_8)).isEqualTo("challenge-abc");
    }

    @Test
    void verificationWithWrongTokenResponds403() throws Exception {
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute((f, t) -> {}, VERIFY_TOKEN);
        CaptureResponse resp = new CaptureResponse();
        Map<String, String> headers = Map.of(
            "hub.mode", "subscribe",
            "hub.challenge", "challenge-abc",
            "hub.verify_token", "wrong-token"
        );
        route.handler().handle("GET", "/webhook/whatsapp", headers, emptyBody(), resp);
        assertThat(resp.status).isEqualTo(403);
    }

    @Test
    void verificationWithMissingParamsReturns200Empty() throws Exception {
        // Meta sends these as query params; our adapter may not forward them as headers.
        // Route returns 200 with empty body as a safe fallback.
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute((f, t) -> {}, VERIFY_TOKEN);
        CaptureResponse resp = new CaptureResponse();
        route.handler().handle("GET", "/webhook/whatsapp", Map.of(), emptyBody(), resp);
        assertThat(resp.status).isEqualTo(200);
    }

    // ── Inbound message ───────────────────────────────────────────────────────

    @Test
    void inboundTextMessageCallsHandler() throws Exception {
        List<String[]> received = new ArrayList<>();
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute(
            (from, text) -> received.add(new String[]{from, text}),
            VERIFY_TOKEN
        );
        CaptureResponse resp = new CaptureResponse();
        String payload = """
            {
              "object": "whatsapp_business_account",
              "entry": [{
                "id": "12345",
                "changes": [{
                  "value": {
                    "messaging_product": "whatsapp",
                    "messages": [{
                      "from": "254700000001",
                      "id": "wamid.abc",
                      "timestamp": "1711000000",
                      "type": "text",
                      "text": { "body": "Consignment C-042 arrived Gulu" }
                    }]
                  },
                  "field": "messages"
                }]
              }]
            }
            """;

        route.handler().handle("POST", "/webhook/whatsapp", Map.of(),
            bodyOf(payload), resp);

        assertThat(resp.status).isEqualTo(200);
        assertThat(received).hasSize(1);
        assertThat(received.get(0)[0]).isEqualTo("254700000001");
        assertThat(received.get(0)[1]).isEqualTo("Consignment C-042 arrived Gulu");
    }

    @Test
    void multipleMessagesInOnePayloadCallsHandlerForEach() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute(
            (from, text) -> count.incrementAndGet(), VERIFY_TOKEN
        );
        CaptureResponse resp = new CaptureResponse();
        String payload = """
            {
              "entry": [{
                "changes": [{
                  "value": {
                    "messages": [
                      {"from":"111","type":"text","text":{"body":"msg1"}},
                      {"from":"222","type":"text","text":{"body":"msg2"}}
                    ]
                  }
                }]
              }]
            }
            """;
        route.handler().handle("POST", "/webhook/whatsapp", Map.of(), bodyOf(payload), resp);
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void nonTextMessageTypesAreIgnored() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute(
            (from, text) -> count.incrementAndGet(), VERIFY_TOKEN
        );
        CaptureResponse resp = new CaptureResponse();
        String payload = """
            {
              "entry": [{
                "changes": [{
                  "value": {
                    "messages": [
                      {"from":"111","type":"image","image":{"id":"img123"}},
                      {"from":"222","type":"text","text":{"body":"hello"}}
                    ]
                  }
                }]
              }]
            }
            """;
        route.handler().handle("POST", "/webhook/whatsapp", Map.of(), bodyOf(payload), resp);
        assertThat(count.get()).isEqualTo(1); // only the text message
    }

    @Test
    void invalidJsonReturns400() throws Exception {
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute((f, t) -> {}, VERIFY_TOKEN);
        CaptureResponse resp = new CaptureResponse();
        route.handler().handle("POST", "/webhook/whatsapp", Map.of(),
            bodyOf("not-json-at-all"), resp);
        assertThat(resp.status).isEqualTo(400);
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute((f, t) -> {}, VERIFY_TOKEN);
        CaptureResponse resp = new CaptureResponse();
        route.handler().handle("DELETE", "/webhook/whatsapp", Map.of(), emptyBody(), resp);
        assertThat(resp.status).isEqualTo(405);
    }

    @Test
    void emptyPostBodyReturns200WithEmptyStatus() throws Exception {
        WhatsAppWebhookRoute route = new WhatsAppWebhookRoute((f, t) -> {}, VERIFY_TOKEN);
        CaptureResponse resp = new CaptureResponse();
        route.handler().handle("POST", "/webhook/whatsapp", Map.of(), emptyBody(), resp);
        assertThat(resp.status).isEqualTo(200);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static InputStream emptyBody() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private static InputStream bodyOf(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static final class CaptureResponse implements RouteResponse {
        int status = 200;
        byte[] body = new byte[0];
        final List<String[]> headers = new ArrayList<>();

        @Override public void status(int code) { this.status = code; }
        @Override public void header(String name, String value) { headers.add(new String[]{name, value}); }
        @Override public void body(byte[] bytes) { this.body = bytes; }
        @Override public void json(String json) { this.body = json.getBytes(StandardCharsets.UTF_8); }
    }
}
