package io.cognis.core.provider;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.model.ChatMessage;
import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicProviderTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldParseTextAndToolUseFromMessagesApi() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "content": [
                    {"type": "text", "text": "partial answer"},
                    {"type": "tool_use", "id": "tool_1", "name": "web_search", "input": {"query": "java"}}
                  ],
                  "usage": {"input_tokens": 10, "output_tokens": 7}
                }
                """));

        AnthropicProvider provider = new AnthropicProvider(
            "anthropic",
            "sk-ant",
            server.url("/v1/").toString(),
            1
        );

        LlmResponse response = provider.chat(
            "claude-sonnet-4-5",
            List.of(ChatMessage.system("sys"), ChatMessage.user("hi")),
            List.of()
        );

        assertThat(response.content()).isEqualTo("partial answer");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().getFirst().name()).isEqualTo("web_search");
        assertThat(response.toolCalls().getFirst().arguments()).containsEntry("query", "java");
        assertThat(response.usage()).containsEntry("input_tokens", 10);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/messages");
        assertThat(request.getHeader("x-api-key")).isEqualTo("sk-ant");
        assertThat(request.getBody().readUtf8()).contains("\"model\":\"claude-sonnet-4-5\"");
    }
}
