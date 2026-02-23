package io.cognis.core.provider;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.model.ChatMessage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatProviderTest {

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
    void shouldParseJsonCompletionResponse() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "choices": [
                    { "message": { "content": "hello from json" } }
                  ],
                  "usage": { "total_tokens": 42 }
                }
                """));

        OpenAiCompatProvider provider = new OpenAiCompatProvider(
            "openrouter",
            "sk-test",
            server.url("/v1").toString(),
            Map.of("X-App", "cognis")
        );

        LlmResponse response = provider.chat("gpt-4.1", List.of(ChatMessage.user("hi")), List.of());

        assertThat(response.content()).isEqualTo("hello from json");
        assertThat(response.usage()).containsEntry("total_tokens", 42);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-test");
        assertThat(request.getHeader("X-App")).isEqualTo("cognis");
        assertThat(request.getBody().readUtf8()).contains("\"stream\":true");
    }

    @Test
    void shouldParseSseCompletionAndToolCalls() {
        String sse = """
            data: {"choices":[{"delta":{"content":"hello "}}]}
            
            data: {"choices":[{"delta":{"content":"world"}}]}
            
            data: {"choices":[{"delta":{"tool_calls":[{"id":"call_1","index":0,"function":{"name":"echo","arguments":"{\\\"text\\\":\\\"hi\\\"}"}}]}}]}
            
            data: {"usage":{"total_tokens":13}}
            
            data: [DONE]
            
            """;

        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sse));

        OpenAiCompatProvider provider = new OpenAiCompatProvider(
            "openrouter",
            "sk-test",
            server.url("/v1/").toString(),
            Map.of()
        );

        LlmResponse response = provider.chat("gpt-4.1", List.of(ChatMessage.user("hi")), List.of());

        assertThat(response.content()).isEqualTo("hello world");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().getFirst().name()).isEqualTo("echo");
        assertThat(response.toolCalls().getFirst().arguments()).containsEntry("text", "hi");
        assertThat(response.usage()).containsEntry("total_tokens", 13);
    }
}
