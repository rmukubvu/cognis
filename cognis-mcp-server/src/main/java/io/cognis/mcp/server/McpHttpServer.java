package io.cognis.mcp.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.mcp.server.model.ToolCallResponse;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.IOException;
import java.util.Deque;
import java.util.Map;

public final class McpHttpServer {
    private final Undertow undertow;

    public McpHttpServer(int port, ToolRouter router, ObjectMapper mapper) {
        HttpHandler handler = exchange -> route(exchange, router, mapper);
        this.undertow = Undertow.builder()
            .addHttpListener(port, "0.0.0.0")
            .setHandler(handler)
            .build();
    }

    public void start() {
        undertow.start();
    }

    public void stop() {
        undertow.stop();
    }

    private void route(HttpServerExchange exchange, ToolRouter router, ObjectMapper mapper) throws Exception {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

        if (exchange.getRequestMethod().equalToString("GET") && exchange.getRequestPath().equals("/healthz")) {
            writeJson(exchange, mapper, Map.of("status", "ok"));
            return;
        }

        if (exchange.getRequestMethod().equalToString("GET") && exchange.getRequestPath().equals("/mcp/tools")) {
            writeJson(exchange, mapper, Map.of("tools", router.listTools()));
            return;
        }

        if (exchange.getRequestMethod().equalToString("POST") && exchange.getRequestPath().equals("/mcp/call")) {
            exchange.startBlocking();
            Map<String, Object> request = mapper.readValue(exchange.getInputStream(), new TypeReference<>() {});
            String name = String.valueOf(request.getOrDefault("name", ""));
            @SuppressWarnings("unchecked")
            Map<String, Object> args = request.get("arguments") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            ToolCallResponse response = router.callTool(name, args);
            int status = response.ok() ? 200 : 400;
            exchange.setStatusCode(status);
            writeJson(exchange, mapper, response);
            return;
        }

        exchange.setStatusCode(404);
        writeJson(exchange, mapper, Map.of("error", "Not found"));
    }

    private void writeJson(HttpServerExchange exchange, ObjectMapper mapper, Object payload) throws IOException {
        exchange.getResponseSender().send(mapper.writeValueAsString(payload));
    }
}
