package io.cognis.mcp.server.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import io.cognis.mcp.server.model.ToolCallResponse;
import java.io.IOException;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class StripeProviderTest {
    @Test
    void sendsBearerAuthAndExpectedPath() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"pi_123\"}"));
            server.start();

            ProviderConfig config = new ProviderConfig(
                "stripe",
                server.url("/").toString().replaceAll("/$", ""),
                "sk_test_123",
                "",
                ""
            );
            StripeProvider provider = new StripeProvider(config, new ProviderHttpClient(new OkHttpClient(), new ObjectMapper()));

            ToolCallResponse response = provider.execute(
                "stripe.create_payment_intent",
                Map.of("body", Map.of("amount", 1000, "currency", "usd"))
            );

            assertThat(response.ok()).isTrue();

            RecordedRequest request = server.takeRequest();
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getPath()).isEqualTo("/payment_intents");
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk_test_123");
            assertThat(request.getBody().readUtf8()).contains("amount");
        }
    }

    @Test
    void returnsErrorWhenProviderNotConfigured() {
        ProviderConfig config = new ProviderConfig("stripe", "https://api.stripe.com/v1", "", "", "");
        StripeProvider provider = new StripeProvider(config, new ProviderHttpClient(new OkHttpClient(), new ObjectMapper()));

        ToolCallResponse response = provider.execute("stripe.create_payment_intent", Map.of("body", Map.of()));

        assertThat(response.ok()).isFalse();
        assertThat(response.message()).contains("not configured");
    }
}
