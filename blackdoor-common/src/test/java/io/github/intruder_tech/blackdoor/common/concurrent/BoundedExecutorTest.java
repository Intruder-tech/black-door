package io.github.intruder_tech.blackdoor.common.concurrent;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedExecutorTest {

    private static final BdLogger SILENT = new BdLogger() {
        @Override
        public void debug(String message) {
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message, Throwable error) {
        }
    };

    @Test
    void rejectsWhenQueueIsFullWithoutBlocking() throws Exception {
        BoundedExecutor executor = new BoundedExecutor("test", 1, 2, SILENT);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        assertThat(executor.trySubmit(() -> {
            started.countDown();
            await(release);
        })).isTrue();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.trySubmit(() -> { })).isTrue();
        assertThat(executor.trySubmit(() -> { })).isTrue();
        assertThat(executor.trySubmit(() -> { })).isFalse();
        assertThat(executor.stats().rejected()).isEqualTo(1);
        release.countDown();
        assertThat(executor.shutdown(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void taskExceptionsAreContainedAndCounted() throws Exception {
        BoundedExecutor executor = new BoundedExecutor("test", 1, 4, SILENT);
        AtomicInteger ran = new AtomicInteger();
        executor.trySubmit(() -> {
            throw new IllegalStateException("boom");
        });
        executor.trySubmit(ran::incrementAndGet);
        assertThat(executor.shutdown(Duration.ofSeconds(5))).isTrue();
        assertThat(ran.get()).isEqualTo(1);
        assertThat(executor.stats().failed()).isEqualTo(1);
    }

    @Test
    void refusesWorkAfterShutdown() {
        BoundedExecutor executor = new BoundedExecutor("test", 1, 4, SILENT);
        executor.shutdown(Duration.ofSeconds(5));
        assertThat(executor.trySubmit(() -> { })).isFalse();
        assertThat(executor.stats().shutdown()).isTrue();
    }

    @Test
    void threadsAreNamedAndDaemon() throws Exception {
        BoundedExecutor executor = new BoundedExecutor("naming", 1, 4, SILENT);
        String[] name = new String[1];
        boolean[] daemon = new boolean[1];
        CountDownLatch done = new CountDownLatch(1);
        executor.trySubmit(() -> {
            name[0] = Thread.currentThread().getName();
            daemon[0] = Thread.currentThread().isDaemon();
            done.countDown();
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(name[0]).startsWith("blackdoor-naming-");
        assertThat(daemon[0]).isTrue();
        executor.shutdown(Duration.ofSeconds(5));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
