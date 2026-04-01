package io.cognis.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    void simpleConstructorHasNoDependencies() {
        Task t = new Task("fetch", "fetch data", "researcher");
        assertThat(t.id()).isEqualTo("fetch");
        assertThat(t.prompt()).isEqualTo("fetch data");
        assertThat(t.role()).isEqualTo("researcher");
        assertThat(t.dependsOn()).isEmpty();
        assertThat(t.toolAllowlist()).isNull();
        assertThat(t.model()).isNull();
    }

    @Test
    void fullConstructorPreservesFields() {
        Task t = new Task("write", "write report", "writer", List.of("fetch"), List.of("file"), "haiku");
        assertThat(t.dependsOn()).containsExactly("fetch");
        assertThat(t.toolAllowlist()).containsExactly("file");
        assertThat(t.model()).isEqualTo("haiku");
    }

    @Test
    void nullDependsOnNormalisedToEmpty() {
        Task t = new Task("x", "do x", "worker", null, null, null);
        assertThat(t.dependsOn()).isEmpty();
    }

    @Test
    void dependsOnIsImmutable() {
        Task t = new Task("x", "do x", "worker", List.of("a"), null, null);
        assertThatThrownBy(() -> t.dependsOn().add("b"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
