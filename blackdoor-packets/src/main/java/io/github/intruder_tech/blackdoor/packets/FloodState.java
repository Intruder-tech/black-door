package io.github.intruder_tech.blackdoor.packets;

/**
 * Per-connection rate limiters. Single writer (the connection's network thread), so no locking.
 * Findings for a category are reported at most once per second, with the number of excess
 * packets since the last report, so a flood cannot flood the ledger.
 */
final class FloodState {

    enum Category { MOVEMENT, INTERACT, TEXT, TOTAL }

    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;

    private final Bucket[] buckets = new Bucket[Category.values().length];
    private final long[] excess = new long[Category.values().length];
    private final long[] lastReport = new long[Category.values().length];

    FloodState(PacketSettings settings, long nowNanos) {
        buckets[Category.MOVEMENT.ordinal()] = new Bucket(settings.movementRate(), nowNanos);
        buckets[Category.INTERACT.ordinal()] = new Bucket(settings.interactRate(), nowNanos);
        buckets[Category.TEXT.ordinal()] = new Bucket(settings.textRate(), nowNanos);
        buckets[Category.TOTAL.ordinal()] = new Bucket(settings.totalRate(), nowNanos);
        for (int i = 0; i < lastReport.length; i++) {
            lastReport[i] = nowNanos - REPORT_INTERVAL_NANOS;
        }
    }

    /** @return true if the packet is within the limit */
    boolean tryAcquire(Category category, long nowNanos) {
        if (buckets[category.ordinal()].tryAcquire(nowNanos)) {
            return true;
        }
        excess[category.ordinal()]++;
        return false;
    }

    /** Returns the excess count to report now, or -1 if a report was made less than a second ago. */
    long takeReport(Category category, long nowNanos) {
        int i = category.ordinal();
        if (nowNanos - lastReport[i] < REPORT_INTERVAL_NANOS) {
            return -1;
        }
        lastReport[i] = nowNanos;
        long count = excess[i];
        excess[i] = 0;
        return count;
    }

    private static final class Bucket {
        private final double capacity;
        private final double perNano;
        private double tokens;
        private long last;

        Bucket(Rate rate, long nowNanos) {
            this.capacity = rate.burst();
            this.perNano = rate.perSecond() / 1_000_000_000.0;
            this.tokens = capacity;
            this.last = nowNanos;
        }

        boolean tryAcquire(long now) {
            long elapsed = now - last;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * perNano);
                last = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
