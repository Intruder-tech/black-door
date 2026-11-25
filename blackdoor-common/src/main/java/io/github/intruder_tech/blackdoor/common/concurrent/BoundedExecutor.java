package io.github.intruder_tech.blackdoor.common.concurrent;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-size worker pool with a bounded queue (SR-10). Submissions never block: when the queue is
 * full, {@link #trySubmit(Runnable)} returns {@code false} and the rejection is counted.
 *
 * <p>Used for blocking work (file I/O now; storage and webhooks later) so that it never runs on
 * Netty event loops or server threads (SR-20).
 */
public final class BoundedExecutor {

    /** Prefix of every thread created by Black Door executors. */
    public static final String THREAD_PREFIX = "blackdoor-";

    private final String name;
    private final NamedDaemonThreadFactory threadFactory;
    private final ThreadPoolExecutor pool;
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final BdLogger logger;

    public BoundedExecutor(String name, int threads, int queueCapacity, BdLogger logger) {
        if (threads < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("threads and queueCapacity must be >= 1");
        }
        this.name = Objects.requireNonNull(name, "name");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.threadFactory = new NamedDaemonThreadFactory(THREAD_PREFIX + name);
        this.pool = new ThreadPoolExecutor(
                threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Queues a task. Returns {@code false} (and counts a rejection) if the queue is full or the
     * executor is shut down. Exceptions thrown by the task are logged and counted, never propagated.
     */
    public boolean trySubmit(Runnable task) {
        Objects.requireNonNull(task, "task");
        try {
            pool.execute(() -> runGuarded(task));
            return true;
        } catch (RejectedExecutionException e) {
            rejected.incrementAndGet();
            return false;
        }
    }

    private void runGuarded(Runnable task) {
        try {
            task.run();
        } catch (VirtualMachineError e) {
            throw e;
        } catch (RuntimeException | Error e) {
            failed.incrementAndGet();
            logger.error("Task failed on executor '" + name + "'", e);
        }
    }

    /**
     * Stops accepting work, waits up to {@code timeout} for queued tasks, then interrupts.
     * Returns {@code true} only when every worker thread has actually exited, so callers can rely on
     * "no Black Door threads left" after a successful shutdown (pool termination alone fires
     * slightly before the threads finish dying).
     */
    public boolean shutdown(Duration timeout) {
        pool.shutdown();
        try {
            boolean terminated = pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!terminated) {
                pool.shutdownNow();
                terminated = pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            return terminated && threadFactory.joinAll(timeout);
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public Stats stats() {
        int queued = pool.getQueue().size();
        return new Stats(name, pool.getMaximumPoolSize(), queued,
                pool.getQueue().remainingCapacity() + queued,
                pool.getActiveCount(), pool.getCompletedTaskCount(), rejected.get(), failed.get(),
                pool.isShutdown());
    }

    /** Point-in-time executor statistics for diagnostics. */
    public record Stats(String name, int threads, int queued, int queueCapacity, int active,
                        long completed, long rejected, long failed, boolean shutdown) {
    }

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();
        /** Bounded: the pool is fixed-size and tasks never kill their worker (runGuarded). */
        private final java.util.List<Thread> created = new java.util.concurrent.CopyOnWriteArrayList<>();

        NamedDaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            created.add(thread);
            return thread;
        }

        boolean joinAll(Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            for (Thread thread : created) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMillis > 0) {
                    thread.join(remainingMillis);
                }
                if (thread.isAlive()) {
                    return false;
                }
            }
            return true;
        }
    }
}
