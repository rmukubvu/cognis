package io.cognis.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentResultTest {

    @Test
    void legacyConstructorDefaultsToSuccess() {
        AgentResult result = new AgentResult("ok", List.of(), Map.of());
        assertThat(result.status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void maxIterationsFactory() {
        AgentResult result = AgentResult.maxIterations("timed out", List.of(), Map.of());
        assertThat(result.status()).isEqualTo(AgentStatus.MAX_ITERATIONS);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.content()).isEqualTo("timed out");
        assertThat(result.errorMessage()).isNotBlank();
    }

    @Test
    void fullConstructorPreservesStatus() {
        AgentResult result = new AgentResult("err", List.of(), Map.of(), AgentStatus.TOOL_ERROR, "bad tool");
        assertThat(result.status()).isEqualTo(AgentStatus.TOOL_ERROR);
        assertThat(result.errorMessage()).isEqualTo("bad tool");
    }
}
