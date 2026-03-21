package io.cognis.core.memory;

import java.io.IOException;
import java.util.List;

/**
 * Produces a fixed-dimension vector embedding for a piece of text.
 *
 * <p>The embedding is used by {@link FileMemoryStore} for cosine-similarity recall.
 * Two implementations ship with cognis-core:
 * <ul>
 *   <li>{@link HashEmbeddingProvider} — deterministic, zero-latency, no external calls.
 *       Good enough for recall on small memory stores (&lt;1 000 entries).</li>
 *   <li>{@link OpenAiCompatEmbeddingProvider} — calls an OpenAI-compatible
 *       {@code /embeddings} endpoint (OpenRouter, OpenAI, etc.). Semantically rich
 *       embeddings for large stores and cross-lingual recall.</li>
 * </ul>
 */
public interface EmbeddingProvider {

    /**
     * Produce a normalised embedding vector for {@code text}.
     *
     * @param text non-null, non-blank input
     * @return unit-length vector of floats; same dimension for all calls
     * @throws IOException if the provider cannot produce an embedding (network error, etc.)
     */
    List<Double> embed(String text) throws IOException;
}
