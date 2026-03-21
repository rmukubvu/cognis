package io.cognis.sdk;

import java.io.InputStream;
import java.util.Map;

/**
 * Handles an inbound HTTP request for a vertical-registered route.
 *
 * <p>Implementations must write a complete HTTP response via the provided {@link RouteResponse}.
 * The underlying transport (Undertow) is hidden behind this abstraction so vertical authors
 * never take a compile-time dependency on the server framework.
 */
@FunctionalInterface
public interface RouteHandler {

    /**
     * @param method   HTTP method in upper-case (e.g. {@code "POST"})
     * @param path     full request path including query string
     * @param headers  request headers with lower-case keys
     * @param body     request body stream; empty stream for bodyless methods
     * @param response mutable response handle — set status, headers, write body
     */
    void handle(
        String method,
        String path,
        Map<String, String> headers,
        InputStream body,
        RouteResponse response
    ) throws Exception;
}
