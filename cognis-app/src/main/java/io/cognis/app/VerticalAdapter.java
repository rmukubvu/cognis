package io.cognis.app;

import io.cognis.sdk.RouteHandler;
import io.cognis.sdk.RouteResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts {@link RouteHandler} (SDK, framework-agnostic) to Undertow's {@link HttpHandler}.
 *
 * <p>Assumes the exchange is already on a worker thread (the dispatch guard in
 * {@code GatewayServer.start()} takes care of that for registered vertical routes).
 */
final class VerticalAdapter {

    private VerticalAdapter() {}

    static HttpHandler toUndertowHandler(RouteHandler routeHandler) {
        return exchange -> {
            exchange.startBlocking();

            Map<String, String> headers = buildHeaders(exchange);
            String method = exchange.getRequestMethod().toString();
            String path = buildFullPath(exchange);

            UndertowRouteResponse response = new UndertowRouteResponse(exchange);
            try {
                routeHandler.handle(method, path, headers, exchange.getInputStream(), response);
            } catch (Exception e) {
                sendError(exchange, e);
            }
        };
    }

    private static Map<String, String> buildHeaders(HttpServerExchange exchange) {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach(entry ->
            headers.put(entry.getHeaderName().toString().toLowerCase(), entry.getFirst())
        );
        return headers;
    }

    private static String buildFullPath(HttpServerExchange exchange) {
        String path = exchange.getRequestURI();
        String query = exchange.getQueryString();
        return (query == null || query.isBlank()) ? path : path + "?" + query;
    }

    private static void sendError(HttpServerExchange exchange, Exception e) {
        try {
            String msg = e.getMessage() == null ? "internal_error" : e.getMessage().replace("\"", "'");
            byte[] body = ("{\"error\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.setStatusCode(500);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(body.length));
            exchange.getResponseSender().send(ByteBuffer.wrap(body));
        } catch (Exception ignored) {
        }
    }

    static final class UndertowRouteResponse implements RouteResponse {
        private final HttpServerExchange exchange;

        UndertowRouteResponse(HttpServerExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public void status(int code) {
            exchange.setStatusCode(code);
        }

        @Override
        public void header(String name, String value) {
            exchange.getResponseHeaders().put(new HttpString(name), value);
        }

        @Override
        public void body(byte[] bytes) {
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(bytes.length));
            exchange.getResponseSender().send(ByteBuffer.wrap(bytes));
        }

        @Override
        public void json(String json) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(bytes.length));
            exchange.getResponseSender().send(ByteBuffer.wrap(bytes));
        }
    }
}
