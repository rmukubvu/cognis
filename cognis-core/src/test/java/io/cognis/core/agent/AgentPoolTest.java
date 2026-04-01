package io.cognis.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentPoolTest {

    private AgentPool pool;

    @AfterEach
    void tearDown() {
        if (pool != null) pool.shutdown();
    }

    @Test
    void maxConcurrentIsRespected() throws Exception {
        pool = new AgentPool(Executors.newVirtualThreadPerTaskExecutor(), 2);
        assertThat(pool.maxConcurrent()).isEqualTo(2);
        assertThat(pool.availablePermits()).isEqualTo(2);

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        pool.submit(() -> { started.countDown(); release.await(); return "a"; });
        pool.submit(() -> { started.countDown(); release.await(); return "b"; });

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(pool.availablePermits()).isEqualTo(0);

        release.countDown();
    }

    @Test
    void rejectsWhenAtCapacity() throws Exception {
        pool = new AgentPool(Executors.newVirtualThreadPerTaskExecutor(), 1);

        CountDownLatch hold = new CountDownLatch(1);
        pool.submit(() -> { hold.await(); return "a"; });

        // Give the first task time to acquire the permit
        Thread.sleep(50);

        Future<String> rejected = pool.submit(() -> "b");
        assertThatThrownBy(() -> rejected.get(500, TimeUnit.MILLISECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(AgentPool.AgentPoolFullException.class);

        hold.countDown();
    }

    @Test
    void permitReleasedAfterCompletion() throws Exception {
        pool = new AgentPool(Executors.newVirtualThreadPerTaskExecutor(), 1);
        Future<String> f = pool.submit(() -> "done");
        assertThat(f.get(2, TimeUnit.SECONDS)).isEqualTo("done");

        // After completion, permit should be back
        assertThat(pool.availablePermits()).isEqualTo(1);
    }

    @Test
    void permitReleasedOnException() throws Exception {
        pool = new AgentPool(Executors.newVirtualThreadPerTaskExecutor(), 1);
        Future<String> f = pool.submit(() -> { throw new RuntimeException("oops"); });
        assertThatThrownBy(() -> f.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);

        // Permit must be released even when callable throws
        assertThat(pool.availablePermits()).isEqualTo(1);
    }
}
