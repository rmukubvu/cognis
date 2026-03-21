package io.cognis.vertical.humanitarian.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.sdk.RouteDefinition;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SmsWebhookRouteTest {

    @Test
    void routeMetadataIsCorrect() {
        SmsWebhookRoute route = new SmsWebhookRoute((f, t) -> {});
        assertThat(route.method()).isEqualTo("POST");
        assertThat(route.path()).isEqualTo("/webhook/sms");
        assertThat(route.handler()).isNotNull();
    }

    @Test
    void parsesTwilioPayloadAndCallsHandler() throws Exception {
        List<String> captured = new ArrayList<>();
        SmsWebhookRoute route = new SmsWebhookRoute((from, body) -> {
            captured.add(from);
            captured.add(body);
        });

        String payload = "From=%2B254700000000&Body=Hello+world&To=%2B15005550006";
        invoke(route, payload);

        assertThat(captured).containsExactly("+254700000000", "Hello world");
    }

    @Test
    void parsesAfricasTalkingPayloadAndCallsHandler() throws Exception {
        List<String> captured = new ArrayList<>();
        SmsWebhookRoute route = new SmsWebhookRoute((from, body) -> {
            captured.add(from);
            captured.add(body);
        });

        String payload = "from=%2B254711111111&text=Field+report+received&to=%2B15005550006";
        invoke(route, payload);

        assertThat(captured).containsExactly("+254711111111", "Field report received");
    }

    @Test
    void alwaysResponds200EvenIfHandlerThrows() throws Exception {
        SmsWebhookRoute route = new SmsWebhookRoute((from, body) -> {
            throw new RuntimeException("simulated failure");
        });

        AtomicInteger statusCode = new AtomicInteger(-1);
        String payload = "From=%2B1234567890&Body=Test";
        CapturingRouteResponse response = new CapturingRouteResponse(statusCode);

        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        InputStream body = new ByteArrayInputStream(bytes);
        route.handler().handle("POST", "/webhook/sms", Map.of(), body, response);

        assertThat(statusCode.get()).isEqualTo(200);
    }

    @Test
    void emptyPayloadDoesNotCallHandler() throws Exception {
        List<String> captured = new ArrayList<>();
        SmsWebhookRoute route = new SmsWebhookRoute((from, body) -> captured.add(from));

        invoke(route, "");

        assertThat(captured).isEmpty();
    }

    private static void invoke(RouteDefinition route, String payload) throws Exception {
        AtomicInteger status = new AtomicInteger();
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        InputStream body = new ByteArrayInputStream(bytes);
        route.handler().handle("POST", "/webhook/sms", Map.of(), body, new CapturingRouteResponse(status));
    }

    private static final class CapturingRouteResponse implements io.cognis.sdk.RouteResponse {
        private final AtomicInteger statusCode;

        CapturingRouteResponse(AtomicInteger statusCode) {
            this.statusCode = statusCode;
        }

        @Override public void status(int code)               { statusCode.set(code); }
        @Override public void header(String name, String v)  {}
        @Override public void body(byte[] bytes)             {}
        @Override public void json(String json)              {}
    }
}
