package io.cognis.core.integration.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class McpToolClientTest {

    @Test
    void listToolsAndCallToolRoundTrip() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"tools\":[{\"name\":\"x.echo\"}]}"));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"data\":{\"sid\":\"123\"}}"));
            server.start();

            McpToolClient client = new McpToolClient(server.url("/").toString());

            var tools = client.listTools();
            var call = client.callTool("x.echo", java.util.Map.of("v", 1));

            assertThat(tools).containsKey("tools");
            assertThat(call).containsEntry("ok", true);
            assertThat(call).containsEntry("http_status", 200);

            var req1 = server.takeRequest();
            assertThat(req1.getMethod()).isEqualTo("GET");
            assertThat(req1.getPath()).isEqualTo("/mcp/tools");

            var req2 = server.takeRequest();
            assertThat(req2.getMethod()).isEqualTo("POST");
            assertThat(req2.getPath()).isEqualTo("/mcp/call");
            assertThat(req2.getBody().readUtf8()).contains("x.echo");
        }
    }
}
