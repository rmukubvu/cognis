package io.cognis.sdk;

/**
 * Describes a single HTTP route that a {@link CognisVertical} wants to register
 * with the Cognis gateway.
 *
 * <p>Implement this interface (or use the {@link #of} factory) to expose webhooks,
 * custom API endpoints, or any other HTTP surface area that belongs to your vertical.
 *
 * <p>Example:
 * <pre>{@code
 * RouteDefinition smsWebhook = RouteDefinition.of("POST", "/webhook/sms", (method, path, headers, body, response) -> {
 *     // parse incoming SMS payload and respond
 *     response.status(200);
 *     response.json("{\"status\":\"accepted\"}");
 * });
 * }</pre>
 */
public interface RouteDefinition {

    /** HTTP method in upper-case, e.g. {@code "GET"} or {@code "POST"}. */
    String method();

    /**
     * Absolute path for this route, e.g. {@code "/webhook/sms"}.
     * Must start with {@code "/"}.
     */
    String path();

    /** The handler that processes requests matching this method + path. */
    RouteHandler handler();

    /** Factory for inline / lambda-based route definitions. */
    static RouteDefinition of(String method, String path, RouteHandler handler) {
        return new RouteDefinition() {
            @Override public String method()       { return method; }
            @Override public String path()         { return path; }
            @Override public RouteHandler handler(){ return handler; }
        };
    }
}
