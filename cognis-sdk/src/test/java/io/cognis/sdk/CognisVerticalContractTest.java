package io.cognis.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.provider.IntegrationProvider;
import io.cognis.core.provider.ToolCallResponse;
import io.cognis.core.provider.ToolDefinition;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the cognis-sdk interfaces.
 *
 * These tests verify that:
 * - All interfaces can be implemented with standard Java (no framework required)
 * - Default methods behave as documented
 * - The RouteDefinition.of() factory is usable inline
 * - CognisVertical wires tools, routes, and providers correctly
 */
class CognisVerticalContractTest {

    // --- RouteDefinition ---

    @Test
    void routeDefinitionOfFactoryReturnsCorrectValues() {
        RouteHandler handler = (method, path, headers, body, response) -> response.json("{}");
        RouteDefinition route = RouteDefinition.of("POST", "/webhook/test", handler);

        assertThat(route.method()).isEqualTo("POST");
        assertThat(route.path()).isEqualTo("/webhook/test");
        assertThat(route.handler()).isSameAs(handler);
    }

    @Test
    void routeHandlerReceivesCorrectArguments() throws Exception {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        RouteDefinition route = RouteDefinition.of("POST", "/test", (method, path, headers, body, response) -> {
            capturedMethod.set(method);
            capturedPath.set(path);
            capturedBody.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            response.status(200);
            response.json("{\"ok\":true}");
        });

        InputStream body = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
        route.handler().handle("POST", "/test?foo=bar", Map.of("content-type", "text/plain"), body, new NoopRouteResponse());

        assertThat(capturedMethod.get()).isEqualTo("POST");
        assertThat(capturedPath.get()).isEqualTo("/test?foo=bar");
        assertThat(capturedBody.get()).isEqualTo("hello");
    }

    // --- RouteResponse ---

    @Test
    void routeResponseCapturingImplementationWorks() {
        CapturingRouteResponse response = new CapturingRouteResponse();
        response.status(201);
        response.header("X-Custom", "value");
        response.json("{\"created\":true}");

        assertThat(response.statusCode).isEqualTo(201);
        assertThat(response.headers).containsEntry("X-Custom", "value");
        assertThat(response.headers).containsEntry("Content-Type", "application/json");
        assertThat(response.body).isEqualTo("{\"created\":true}");
    }

    // --- CognisVertical defaults ---

    @Test
    void cognisVerticalDefaultProvidersReturnsEmptyList() {
        CognisVertical vertical = minimalVertical("test", List.of(), List.of());
        assertThat(vertical.providers()).isEmpty();
    }

    @Test
    void cognisVerticalDefaultInitializeDoesNotThrow() {
        CognisVertical vertical = minimalVertical("test", List.of(), List.of());
        ToolContext context = new ToolContext(Path.of("/tmp"));
        // must not throw
        vertical.initialize(context);
    }

    @Test
    void cognisVerticalExposesToolsAndRoutes() {
        Tool fakeTool = new Tool() {
            @Override public String name()        { return "fake.tool"; }
            @Override public String description() { return "A fake tool for testing"; }
            @Override public String execute(Map<String, Object> input, ToolContext ctx) { return "ok"; }
        };

        RouteDefinition fakeRoute = RouteDefinition.of("GET", "/ping", (m, p, h, b, r) -> r.json("{\"pong\":true}"));

        CognisVertical vertical = minimalVertical("demo", List.of(fakeTool), List.of(fakeRoute));

        assertThat(vertical.name()).isEqualTo("demo");
        assertThat(vertical.tools()).hasSize(1);
        assertThat(vertical.tools().get(0).name()).isEqualTo("fake.tool");
        assertThat(vertical.routes()).hasSize(1);
        assertThat(vertical.routes().get(0).path()).isEqualTo("/ping");
    }

    @Test
    void cognisVerticalWithCustomProvider() {
        IntegrationProvider provider = new IntegrationProvider() {
            @Override public String name() { return "test-provider"; }
            @Override public List<ToolDefinition> tools() {
                return List.of(new ToolDefinition("test.op", "desc", Map.of(), "test-provider", false));
            }
            @Override public ToolCallResponse execute(String toolName, Map<String, Object> arguments) {
                return ToolCallResponse.ok("done", Map.of());
            }
        };

        CognisVertical vertical = new CognisVertical() {
            @Override public String name()                    { return "with-provider"; }
            @Override public List<Tool> tools()               { return List.of(); }
            @Override public List<RouteDefinition> routes()   { return List.of(); }
            @Override public List<IntegrationProvider> providers() { return List.of(provider); }
        };

        assertThat(vertical.providers()).hasSize(1);
        assertThat(vertical.providers().get(0).name()).isEqualTo("test-provider");
        assertThat(vertical.providers().get(0).supports("test.op")).isTrue();
        assertThat(vertical.providers().get(0).supports("missing")).isFalse();
    }

    @Test
    void initializeHookIsCalledWithContext() {
        AtomicBoolean initialized = new AtomicBoolean(false);
        AtomicReference<Path> capturedWorkspace = new AtomicReference<>();

        CognisVertical vertical = new CognisVertical() {
            @Override public String name()                  { return "hook-test"; }
            @Override public List<Tool> tools()             { return List.of(); }
            @Override public List<RouteDefinition> routes() { return List.of(); }
            @Override public void initialize(ToolContext context) {
                initialized.set(true);
                capturedWorkspace.set(context.workspace());
            }
        };

        Path workspace = Path.of("/tmp/cognis-test");
        vertical.initialize(new ToolContext(workspace));

        assertThat(initialized.get()).isTrue();
        assertThat(capturedWorkspace.get()).isEqualTo(workspace);
    }

    // --- Helpers ---

    private static CognisVertical minimalVertical(String name, List<Tool> tools, List<RouteDefinition> routes) {
        return new CognisVertical() {
            @Override public String name()                  { return name; }
            @Override public List<Tool> tools()             { return tools; }
            @Override public List<RouteDefinition> routes() { return routes; }
        };
    }

    private static class NoopRouteResponse implements RouteResponse {
        @Override public void status(int code)              {}
        @Override public void header(String name, String v) {}
        @Override public void body(byte[] bytes)            {}
        @Override public void json(String json)             {}
    }

    private static class CapturingRouteResponse implements RouteResponse {
        int statusCode = 200;
        final Map<String, String> headers = new java.util.LinkedHashMap<>();
        String body;

        @Override public void status(int code)               { this.statusCode = code; }
        @Override public void header(String name, String v)  { headers.put(name, v); }
        @Override public void body(byte[] bytes)             { this.body = new String(bytes, StandardCharsets.UTF_8); }
        @Override public void json(String json)              { headers.put("Content-Type", "application/json"); this.body = json; }
    }
}
