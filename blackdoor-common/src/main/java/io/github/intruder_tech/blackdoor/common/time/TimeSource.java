package io.github.intruder_tech.blackdoor.common.time;

/** Injectable clock so that time-dependent logic is deterministic in tests. */
public interface TimeSource {

    /** Monotonic time in nanoseconds; only differences are meaningful. */
    long nanoTime();

    /** Wall-clock time in milliseconds since the epoch. */
    long currentTimeMillis();

    /** The real system clock. */
    static TimeSource system() {
        return SystemTimeSource.INSTANCE;
    }
}
