package io.cognis.core.provider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ProviderRegistry {
    private final Map<String, LlmProvider> providers = new ConcurrentHashMap<>();

    public void register(LlmProvider provider) {
        providers.put(normalize(provider.name()), provider);
    }

    public Optional<LlmProvider> find(String name) {
        return Optional.ofNullable(providers.get(normalize(name)));
    }

    private String normalize(String name) {
        return name == null ? "" : name.toLowerCase().replace('-', '_');
    }
}
