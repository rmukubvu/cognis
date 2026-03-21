package io.cognis.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.api.GatewayServer;
import io.cognis.core.provider.IntegrationProvider;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.core.tool.ToolRegistry;
import io.cognis.sdk.CognisVertical;
import io.cognis.sdk.RouteDefinition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that tools and routes from a {@link CognisVertical} are correctly wired
 * into the runtime without requiring a full application boot.
 */
class VerticalLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void verticalToolIsRegisteredInToolRegistry() {
        Tool fakeTool = new Tool() {
            @Override public String name()        { return "test.ping"; }
            @Override public String description() { return "Ping tool for testing"; }
            @Override public String execute(Map<String, Object> input, ToolContext ctx) { return "pong"; }
        };

        CognisVertical vertical = minimalVertical(List.of(fakeTool), List.of());
        ToolRegistry registry = new ToolRegistry();

        vertical.initialize(new ToolContext(tempDir));
        vertical.tools().forEach(registry::register);

        assertThat(registry.find("test.ping")).isPresent();
        assertThat(registry.find("test.ping").get().name()).isEqualTo("test.ping");
    }

    @Test
    void verticalInitializeHookReceivesWorkspacePath() {
        AtomicBoolean initialized = new AtomicBoolean(false);
        Path[] captured = new Path[1];

        CognisVertical vertical = new CognisVertical() {
            @Override public String name()                  { return "init-test"; }
            @Override public List<Tool> tools()             { return List.of(); }
            @Override public List<RouteDefinition> routes() { return List.of(); }
            @Override public void initialize(ToolContext ctx) {
                initialized.set(true);
                captured[0] = ctx.workspace();
            }
        };

        vertical.initialize(new ToolContext(tempDir));

        assertThat(initialized.get()).isTrue();
        assertThat(captured[0]).isEqualTo(tempDir);
    }

    @Test
    void verticalRouteIsAdaptedAndServesHttp() throws Exception {
        RouteDefinition route = RouteDefinition.of("POST", "/webhook/test", (method, path, headers, body, response) -> {
            byte[] bytes = body.readAllBytes();
            String echo = new String(bytes, StandardCharsets.UTF_8);
            response.status(200);
            response.json("{\"echo\":\"" + echo + "\"}");
        });

        CognisVertical vertical = minimalVertical(List.of(), List.of(route));

        try (GatewayServer server = new GatewayServer(0, tempDir, ignored -> "")) {
            vertical.routes().forEach(r ->
                server.registerRoute(r.method(), r.path(),
                    VerticalAdapter.toUndertowHandler(r.handler()))
            );
            server.start();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + server.port() + "/webhook/test"))
                .POST(HttpRequest.BodyPublishers.ofString("hello"))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"echo\":\"hello\"");
        }
    }

    @Test
    void verticalDefaultProvidersIsEmpty() {
        CognisVertical vertical = minimalVertical(List.of(), List.of());
        List<IntegrationProvider> providers = vertical.providers();
        assertThat(providers).isEmpty();
    }

    // --- helpers ---

    private static CognisVertical minimalVertical(List<Tool> tools, List<RouteDefinition> routes) {
        return new CognisVertical() {
            @Override public String name()                  { return "test-vertical"; }
            @Override public List<Tool> tools()             { return tools; }
            @Override public List<RouteDefinition> routes() { return routes; }
        };
    }
}
