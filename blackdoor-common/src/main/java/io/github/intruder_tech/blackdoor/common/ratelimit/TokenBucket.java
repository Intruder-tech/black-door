package io.github.intruder_tech.blackdoor.common.ratelimit;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;

import java.util.Objects;

/**
 * Classic token bucket: holds up to {@code capacity} tokens, refilled continuously at
 * {@code refillPerSecond}. O(1), allocation-free.
 *
 * <p>Thread-safe via a monitor. Intended for administrative and logging paths; the per-packet
 * hot path (Phase 2) uses single-writer variants without locking.
 */
public final class TokenBucket {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final double capacity;
    private final double refillPerNano;
    private final TimeSource time;

    private double tokens;
    private long lastRefill;

    public TokenBucket(double capacity, double refillPerSecond, TimeSource time) {
        if (!(capacity >= 1.0) || !Double.isFinite(capacity)) {
            throw new IllegalArgumentException("capacity must be finite and >= 1: " + capacity);
        }
        if (!(refillPerSecond >= 0.0) || !Double.isFinite(refillPerSecond)) {
            throw new IllegalArgumentException("refillPerSecond must be finite and >= 0: " + refillPerSecond);
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / NANOS_PER_SECOND;
        this.time = Objects.requireNonNull(time, "time");
        this.tokens = capacity;
        this.lastRefill = time.nanoTime();
    }

    /** Takes one token if available. */
    public boolean tryAcquire() {
        return tryAcquire(1.0);
    }

    /** Takes {@code amount} tokens if all are available; otherwise takes none. */
    public synchronized boolean tryAcquire(double amount) {
        if (!(amount > 0.0) || !Double.isFinite(amount)) {
            throw new IllegalArgumentException("amount must be finite and > 0: " + amount);
        }
        refill();
        if (tokens >= amount) {
            tokens -= amount;
            return true;
        }
        return false;
    }

    /** Tokens currently available (after refill). */
    public synchronized double available() {
        refill();
        return tokens;
    }

    public double capacity() {
        return capacity;
    }

    private void refill() {
        long now = time.nanoTime();
        long elapsed = now - lastRefill;
        if (elapsed <= 0) {
            return; // clock did not move (or moved backwards): never add tokens
        }
        lastRefill = now;
        tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
    }
}
