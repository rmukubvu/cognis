package io.cognis.core.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cognis.core.model.ChatMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderRouterTest {

    @Test
    void shouldResolveByModelHeuristics() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new StubProvider("anthropic"));
        registry.register(new StubProvider("openai"));
        registry.register(new StubProvider("openrouter"));
        registry.register(new StubProvider("openai_codex"));
        registry.register(new StubProvider("github_copilot"));

        ProviderRouter router = new ProviderRouter(registry);

        assertThat(router.resolve(null, "claude-sonnet").name()).isEqualTo("anthropic");
        assertThat(router.resolve(null, "gpt-5").name()).isEqualTo("openai");
        assertThat(router.resolve(null, "openai-codex/gpt-5-codex").name()).isEqualTo("openai_codex");
        assertThat(router.resolve(null, "copilot/o4-mini").name()).isEqualTo("github_copilot");
        assertThat(router.resolve(null, "some-model").name()).isEqualTo("openrouter");
    }

    @Test
    void shouldFailForUnknownPreferredProvider() {
        ProviderRouter router = new ProviderRouter(new ProviderRegistry());

        assertThatThrownBy(() -> router.resolve("missing", "gpt-5"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown provider");
    }

    @Test
    void shouldResolvePreferredProviderWithHyphenAlias() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new StubProvider("openai_codex"));
        ProviderRouter router = new ProviderRouter(registry);

        assertThat(router.resolve("openai-codex", "ignored").name()).isEqualTo("openai_codex");
    }

    private record StubProvider(String name) implements LlmProvider {
        @Override
        public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
            return new LlmResponse("ok", List.of(), Map.of());
        }
    }
}
