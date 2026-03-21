package io.cognis.vertical.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.sdk.RouteResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExampleRouteTest {

    @Test
    void methodAndPathAreSet() {
        ExampleRoute route = new ExampleRoute();
        assertThat(route.method()).isNotBlank();
        assertThat(route.path()).startsWith("/");
    }

    @Test
    void handlerResponds200() throws Exception {
        ExampleRoute route = new ExampleRoute();
        AtomicInteger status = new AtomicInteger(-1);

        route.handler().handle(
            "POST", "/webhook/example",
            Map.of(),
            new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
            new CapturingRouteResponse(status, new AtomicReference<>())
        );

        assertThat(status.get()).isEqualTo(200);
    }

    @Test
    void handlerWritesNonEmptyBody() throws Exception {
        ExampleRoute route = new ExampleRoute();
        AtomicReference<String> body = new AtomicReference<>();

        route.handler().handle(
            "POST", "/webhook/example",
            Map.of(),
            new ByteArrayInputStream("test payload".getBytes(StandardCharsets.UTF_8)),
            new CapturingRouteResponse(new AtomicInteger(), body)
        );

        assertThat(body.get()).isNotBlank();
    }

    private static final class CapturingRouteResponse implements RouteResponse {
        private final AtomicInteger statusCode;
        private final AtomicReference<String> bodyCapture;

        CapturingRouteResponse(AtomicInteger statusCode, AtomicReference<String> bodyCapture) {
            this.statusCode = statusCode;
            this.bodyCapture = bodyCapture;
        }

        @Override public void status(int code)              { statusCode.set(code); }
        @Override public void header(String name, String v) {}
        @Override public void body(byte[] bytes)            { bodyCapture.set(new String(bytes, StandardCharsets.UTF_8)); }
        @Override public void json(String json)             { bodyCapture.set(json); }
    }
}
