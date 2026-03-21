package io.cognis.sdk;

/**
 * Mutable response handle passed to a {@link RouteHandler}.
 *
 * <p>Set status and headers before writing the body. Calling any {@code body} or {@code json}
 * method commits the response; further header changes are ignored by the underlying transport.
 */
public interface RouteResponse {

    /** Sets the HTTP status code. Defaults to {@code 200} if never called. */
    void status(int code);

    /** Adds or replaces a response header. */
    void header(String name, String value);

    /** Writes raw bytes as the response body. */
    void body(byte[] bytes);

    /**
     * Convenience method: sets {@code Content-Type: application/json} and writes the
     * JSON string as the response body.
     */
    void json(String json);
}
