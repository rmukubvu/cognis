package io.cognis.core.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, zero-dependency embedding provider.
 *
 * <p>Tokenises the input text, maps each token to a dimension via
 * {@code hashCode % DIM}, accumulates term-frequency counts, then L2-normalises
 * the result. Produces 256-dimensional unit vectors.
 *
 * <p>This is the default used by {@link FileMemoryStore} when no external
 * embedding provider is configured. It is fast (no I/O) and deterministic, but
 * does not capture semantic similarity across different words. Upgrade to
 * {@link OpenAiCompatEmbeddingProvider} for multi-lingual or large-store deployments.
 */
public final class HashEmbeddingProvider implements EmbeddingProvider {

    public static final int DIM = 256;

    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "and", "or", "is", "are", "was", "were", "to", "of",
        "in", "for", "on", "with", "at", "by", "from", "it", "this", "that",
        "these", "those", "be", "been", "being", "as", "if", "but", "not", "no",
        "you", "your", "we", "our", "they", "their", "he", "she", "his", "her"
    );

    @Override
    public List<Double> embed(String text) {
        double[] vector = new double[DIM];
        for (String token : tokenize(text)) {
            int index = Math.floorMod(token.hashCode(), DIM);
            vector[index] += 1.0;
        }
        return normalise(vector);
    }

    // ── package-private helpers ──────────────────────────────────────────────

    List<String> tokenize(String text) {
        String[] raw = (text == null ? "" : text).toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String token : raw) {
            if (token.length() > 1 && !STOP_WORDS.contains(token)) {
                out.add(token);
            }
        }
        return out;
    }

    private List<Double> normalise(double[] vector) {
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        List<Double> result = new ArrayList<>(DIM);
        if (norm == 0.0) {
            for (int i = 0; i < DIM; i++) {
                result.add(0.0);
            }
            return result;
        }
        for (double v : vector) {
            result.add(v / norm);
        }
        return result;
    }
}
