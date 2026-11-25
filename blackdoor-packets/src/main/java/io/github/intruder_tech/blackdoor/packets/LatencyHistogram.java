package io.github.intruder_tech.blackdoor.packets;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free log2-bucket histogram of nanosecond durations. Percentiles are reported as the upper
 * bound of the bucket (resolution: factor of two), which is enough to check the performance
 * budget (docs/PERFORMANCE_BUDGET.md).
 */
public final class LatencyHistogram {

    private static final int BUCKETS = 48;
    private final LongAdder[] counts = new LongAdder[BUCKETS];

    public LatencyHistogram() {
        for (int i = 0; i < BUCKETS; i++) {
            counts[i] = new LongAdder();
        }
    }

    public void record(long nanos) {
        long value = Math.max(1, nanos);
        int bucket = Math.min(BUCKETS - 1, 63 - Long.numberOfLeadingZeros(value));
        counts[bucket].increment();
    }

    public long count() {
        long total = 0;
        for (LongAdder c : counts) {
            total += c.sum();
        }
        return total;
    }

    /** Upper bound (nanoseconds) of the bucket containing the given percentile (0-100). */
    public long percentileUpperBoundNanos(double percentile) {
        long[] snapshot = new long[BUCKETS];
        long total = 0;
        for (int i = 0; i < BUCKETS; i++) {
            snapshot[i] = counts[i].sum();
            total += snapshot[i];
        }
        if (total == 0) {
            return 0;
        }
        long threshold = (long) Math.ceil(total * percentile / 100.0);
        long seen = 0;
        for (int i = 0; i < BUCKETS; i++) {
            seen += snapshot[i];
            if (seen >= threshold) {
                return 1L << (i + 1);
            }
        }
        return 1L << BUCKETS;
    }
}
