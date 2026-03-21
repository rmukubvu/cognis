package io.cognis.core.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HashEmbeddingProviderTest {

    private final HashEmbeddingProvider provider = new HashEmbeddingProvider();

    @Test
    void embeddingHasCorrectDimension() throws Exception {
        List<Double> embedding = provider.embed("consignment arrived Gulu");
        assertThat(embedding).hasSize(HashEmbeddingProvider.DIM);
    }

    @Test
    void embeddingIsNormalised() throws Exception {
        List<Double> v = provider.embed("supply tracking tool activated");
        double norm = v.stream().mapToDouble(d -> d * d).sum();
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void sameInputProducesSameEmbedding() throws Exception {
        List<Double> a = provider.embed("morning briefing USAID");
        List<Double> b = provider.embed("morning briefing USAID");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentInputsProduceDifferentEmbeddings() throws Exception {
        List<Double> a = provider.embed("overdue shipment alert");
        List<Double> b = provider.embed("payment invoice received");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void similarTextsHaveHigherCosineSimilarityThanDissimilar() throws Exception {
        List<Double> supply1 = provider.embed("supply consignment dispatched warehouse");
        List<Double> supply2 = provider.embed("consignment delivered warehouse receipt");
        List<Double> unrelated = provider.embed("quarterly budget review finance");

        double simRelated    = cosine(supply1, supply2);
        double simUnrelated  = cosine(supply1, unrelated);

        assertThat(simRelated).isGreaterThan(simUnrelated);
    }

    @Test
    void emptyStringProducesZeroVector() throws Exception {
        List<Double> v = provider.embed("");
        double norm = v.stream().mapToDouble(d -> d * d).sum();
        assertThat(norm).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    private static double cosine(List<Double> a, List<Double> b) {
        double dot = 0.0;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            dot += a.get(i) * b.get(i);
        }
        return dot;
    }
}
