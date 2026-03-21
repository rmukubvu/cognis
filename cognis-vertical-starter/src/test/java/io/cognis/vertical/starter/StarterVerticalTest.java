package io.cognis.vertical.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the starter vertical compiles and wires correctly.
 * Replace or extend these tests as you build out your vertical.
 */
class StarterVerticalTest {

    @TempDir
    Path tempDir;

    @Test
    void nameIsSet() {
        assertThat(new StarterVertical().name()).isEqualTo("starter");
    }

    @Test
    void toolsIsNonEmpty() {
        assertThat(new StarterVertical().tools()).isNotEmpty();
    }

    @Test
    void routesIsNonEmpty() {
        assertThat(new StarterVertical().routes()).isNotEmpty();
    }

    @Test
    void providersIsEmpty() {
        assertThat(new StarterVertical().providers()).isEmpty();
    }

    @Test
    void initializeDoesNotThrow() {
        StarterVertical vertical = new StarterVertical();
        vertical.initialize(new ToolContext(tempDir));
    }

    @Test
    void toolNamesAreUnique() {
        long distinctNames = new StarterVertical().tools().stream()
            .map(t -> t.name())
            .distinct()
            .count();
        assertThat(distinctNames).isEqualTo(new StarterVertical().tools().size());
    }

    @Test
    void routePathsAreUnique() {
        long distinctPaths = new StarterVertical().routes().stream()
            .map(r -> r.method() + ":" + r.path())
            .distinct()
            .count();
        assertThat(distinctPaths).isEqualTo(new StarterVertical().routes().size());
    }
}
