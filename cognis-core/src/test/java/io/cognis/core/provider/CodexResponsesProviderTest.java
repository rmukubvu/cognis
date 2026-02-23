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

class CodexResponsesProviderTest {

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
    void shouldParseSseTextAndToolCall() throws Exception {
        String sse = """
            data: {"type":"response.output_text.delta","delta":"hello "}
            
            data: {"type":"response.output_item.added","item":{"type":"function_call","id":"fc_1","call_id":"call_1","name":"echo","arguments":"{\\\"text\\\":\\\"hi\\\"}"}}
            
            data: {"type":"response.output_text.delta","delta":"world"}
            
            data: {"usage":{"total_tokens":33}}
            
            data: [DONE]
            
            """;

        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sse));

        CodexResponsesProvider provider = new CodexResponsesProvider(
            "openai_codex",
            "token-1",
            "acct-1",
            server.url("/backend-api/codex/responses").toString()
        );

        LlmResponse response = provider.chat("openai-codex/gpt-5-codex", List.of(ChatMessage.user("hi")), List.of());

        assertThat(response.content()).isEqualTo("hello world");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().getFirst().name()).isEqualTo("echo");
        assertThat(response.toolCalls().getFirst().arguments()).containsEntry("text", "hi");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-1");
        assertThat(request.getHeader("chatgpt-account-id")).isEqualTo("acct-1");
        assertThat(request.getBody().readUtf8()).contains("\"model\":\"gpt-5-codex\"");
    }
}
