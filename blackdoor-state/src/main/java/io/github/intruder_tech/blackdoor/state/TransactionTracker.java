package io.github.intruder_tech.blackdoor.state;

/**
 * Black Door's own transactions (ADR-008): ids are taken from a reserved range so replies can be
 * told apart from other plugins' pings. Measures round-trip time and jitter, and orders server
 * events against client acknowledgement via a sequence number.
 *
 * <p>Synchronized: ids are issued by the heartbeat thread and acknowledged on the network thread.
 */
public final class TransactionTracker {

    /** Ping ids (1.17+): [MIN_VALUE, MIN_VALUE + 2^24). */
    public static final int PING_BASE = Integer.MIN_VALUE;
    public static final int PING_SPAN = 1 << 24;
    /** Window-confirmation action numbers (legacy): [-32768, -16385]. */
    public static final int LEGACY_BASE = Short.MIN_VALUE;
    public static final int LEGACY_SPAN = 1 << 14;

    public enum Result { IN_ORDER, OUT_OF_ORDER, UNKNOWN, NOT_OURS }

    private static final double ALPHA = 1.0 / 8.0;

    private final boolean legacy;
    private final int[] ids;
    private final long[] sequences;
    private final long[] sentAt;
    private int head;
    private int size;
    private long issued;
    private long acknowledged;
    private long overflowed;
    private double rttNanos = -1;
    private double jitterNanos;

    public TransactionTracker(boolean legacy, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.legacy = legacy;
        this.ids = new int[capacity];
        this.sequences = new long[capacity];
        this.sentAt = new long[capacity];
    }

    public static boolean isOurs(int id, boolean legacy) {
        if (legacy) {
            return id >= LEGACY_BASE && id < LEGACY_BASE + LEGACY_SPAN;
        }
        return id >= PING_BASE && id < PING_BASE + PING_SPAN;
    }

    public boolean legacy() {
        return legacy;
    }

    /** Issues the next id and records it as sent at {@code nowNanos}. */
    public synchronized int next(long nowNanos) {
        int span = legacy ? LEGACY_SPAN : PING_SPAN;
        int id = (legacy ? LEGACY_BASE : PING_BASE) + (int) (issued % span);
        issued++;
        if (size == ids.length) {
            head = (head + 1) % ids.length;
            size--;
            overflowed++;
        }
        int slot = (head + size) % ids.length;
        ids[slot] = id;
        sequences[slot] = issued;
        sentAt[slot] = nowNanos;
        size++;
        return id;
    }

    public synchronized Result acknowledge(int id, long nowNanos) {
        if (!isOurs(id, legacy)) {
            return Result.NOT_OURS;
        }
        for (int i = 0; i < size; i++) {
            int slot = (head + i) % ids.length;
            if (ids[slot] == id) {
                long rtt = Math.max(0, nowNanos - sentAt[slot]);
                if (rttNanos < 0) {
                    rttNanos = rtt;
                } else {
                    jitterNanos += ALPHA * (Math.abs(rtt - rttNanos) - jitterNanos);
                    rttNanos += ALPHA * (rtt - rttNanos);
                }
                acknowledged = sequences[slot];
                head = (head + i + 1) % ids.length;
                size -= i + 1;
                return i == 0 ? Result.IN_ORDER : Result.OUT_OF_ORDER;
            }
        }
        return Result.UNKNOWN;
    }

    /** Sequence number the next issued transaction will carry. */
    public synchronized long nextSequence() {
        return issued + 1;
    }

    /** Highest sequence number the client has acknowledged. */
    public synchronized long acknowledgedSequence() {
        return acknowledged;
    }

    public synchronized int outstanding() {
        return size;
    }

    public synchronized long overflowed() {
        return overflowed;
    }

    /** Age of the oldest unacknowledged transaction, or 0. */
    public synchronized long oldestOutstandingNanos(long nowNanos) {
        return size == 0 ? 0 : Math.max(0, nowNanos - sentAt[head]);
    }

    /** Smoothed round-trip time in milliseconds, or -1 before the first reply. */
    public synchronized double rttMillis() {
        return rttNanos < 0 ? -1 : rttNanos / 1_000_000.0;
    }

    public synchronized double jitterMillis() {
        return jitterNanos / 1_000_000.0;
    }
}
