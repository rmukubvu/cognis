package io.cognis.mcp.server.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import io.cognis.mcp.server.model.ToolCallResponse;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class TwilioProviderTest {
    @Test
    void expandsAccountPathAndUsesBasicAuth() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"sid\":\"SM123\"}"));
            server.start();

            ProviderConfig config = new ProviderConfig(
                "twilio",
                server.url("/").toString().replaceAll("/$", ""),
                "",
                "AC123",
                "token-abc"
            );
            TwilioProvider provider = new TwilioProvider(config, new ProviderHttpClient(new OkHttpClient(), new ObjectMapper()));

            ToolCallResponse response = provider.execute(
                "twilio.send_sms",
                Map.of("body", Map.of("To", "+15555550123", "From", "+15555550999", "Body", "hello"))
            );

            assertThat(response.ok()).isTrue();

            RecordedRequest request = server.takeRequest();
            assertThat(request.getPath()).isEqualTo("/Accounts/AC123/Messages.json");
            assertThat(request.getHeader("Authorization")).startsWith("Basic ");
        }
    }
}
