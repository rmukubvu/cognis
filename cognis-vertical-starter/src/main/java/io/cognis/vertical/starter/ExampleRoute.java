package io.cognis.vertical.starter;

import io.cognis.sdk.RouteDefinition;
import io.cognis.sdk.RouteHandler;
import java.nio.charset.StandardCharsets;

/**
 * Template HTTP route — rename and implement your webhook or API endpoint.
 *
 * <h2>Checklist</h2>
 * <ol>
 *   <li>Rename the class (e.g. {@code SlackEventRoute}, {@code WebhookReceiver}).</li>
 *   <li>Update {@link #method()} and {@link #path()} to match your endpoint.</li>
 *   <li>Implement {@link #handler()} — parse the request, call your domain logic, write the
 *       response via {@link io.cognis.sdk.RouteResponse}.</li>
 * </ol>
 *
 * <h2>RouteResponse API</h2>
 * <pre>{@code
 * response.status(200);
 * response.header("X-My-Header", "value");
 * response.json("{\"ok\":true}");        // sets Content-Type + body
 * response.body(bytes);                  // raw bytes, set Content-Type header first
 * }</pre>
 *
 * <h2>IO thread safety</h2>
 * Your handler runs on a worker thread — blocking calls (DB, HTTP) are safe.
 * Never call {@code Thread.sleep()} or block indefinitely.
 */
public final class ExampleRoute implements RouteDefinition {

    // TODO: set to "GET", "POST", "PUT", "DELETE", etc.
    @Override
    public String method() {
        return "POST";
    }

    // TODO: set your route path, must start with "/"
    @Override
    public String path() {
        return "/webhook/example";
    }

    // TODO: implement your request handling logic
    @Override
    public RouteHandler handler() {
        return (method, path, headers, body, response) -> {
            byte[] bytes = body.readAllBytes();
            String received = new String(bytes, StandardCharsets.UTF_8);

            // TODO: parse payload, call your service, build a real response
            response.status(200);
            response.json("{\"received\":" + received.length() + "}");
        };
    }
}
