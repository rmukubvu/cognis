package io.cognis.core.provider;

import io.cognis.core.model.ChatMessage;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FallbackLlmProvider implements LlmProvider {
    private static final Logger LOG = LoggerFactory.getLogger(FallbackLlmProvider.class);
    private final String name;
    private final List<LlmProvider> chain;

    public FallbackLlmProvider(String name, List<LlmProvider> chain) {
        this.name = name;
        this.chain = List.copyOf(chain);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
        LlmResponse last = new LlmResponse("Error calling LLM: no providers in fallback chain", List.of(), Map.of());
        for (LlmProvider provider : chain) {
            last = provider.chat(model, messages, tools);
            if (!isError(last)) {
                LOG.debug("Provider {} served request for chain {}", provider.name(), name);
                return last;
            }
            LOG.warn(
                "Provider {} failed in chain {}: {}",
                provider.name(),
                name,
                truncate(last.content(), 300)
            );
        }
        return last;
    }

    private boolean isError(LlmResponse response) {
        String content = response.content();
        return content != null && content.startsWith("Error calling LLM:");
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
