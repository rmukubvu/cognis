package io.cognis.core.provider;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.model.ChatMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FallbackLlmProviderTest {

    @Test
    void shouldUseNextProviderWhenFirstFails() {
        LlmProvider primary = new StubProvider("primary", "Error calling LLM: timeout");
        LlmProvider secondary = new StubProvider("secondary", "ok");

        FallbackLlmProvider provider = new FallbackLlmProvider("openrouter", List.of(primary, secondary));

        LlmResponse response = provider.chat("model", List.of(ChatMessage.user("hi")), List.of());

        assertThat(response.content()).isEqualTo("ok");
    }

    private record StubProvider(String name, String content) implements LlmProvider {
        @Override
        public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
            return new LlmResponse(content, List.of(), Map.of());
        }
    }
}
