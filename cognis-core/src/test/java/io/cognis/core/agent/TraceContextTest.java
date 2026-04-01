package io.cognis.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceContextTest {

    @Test
    void rootHasFreshIds() {
        TraceContext root = TraceContext.root();
        assertThat(root.traceId()).isNotBlank();
        assertThat(root.spanId()).isNotBlank();
        assertThat(root.parentSpanId()).isNull();
    }

    @Test
    void childInheritsTraceId() {
        TraceContext root = TraceContext.root();
        TraceContext child = root.child();
        assertThat(child.traceId()).isEqualTo(root.traceId());
        assertThat(child.spanId()).isNotEqualTo(root.spanId());
        assertThat(child.parentSpanId()).isEqualTo(root.spanId());
    }

    @Test
    void grandchildMaintainsTraceLineage() {
        TraceContext root = TraceContext.root();
        TraceContext child = root.child();
        TraceContext grand = child.child();
        assertThat(grand.traceId()).isEqualTo(root.traceId());
        assertThat(grand.parentSpanId()).isEqualTo(child.spanId());
    }

    @Test
    void twoRootsHaveDifferentTraceIds() {
        assertThat(TraceContext.root().traceId()).isNotEqualTo(TraceContext.root().traceId());
    }
}
