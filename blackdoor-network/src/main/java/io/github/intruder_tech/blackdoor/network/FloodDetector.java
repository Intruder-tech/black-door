package io.github.intruder_tech.blackdoor.network;

import io.github.intruder_tech.blackdoor.common.ratelimit.TokenBucket;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;

import java.util.Arrays;

/**
 * Notices a flood of logins from unknown addresses: more than {@code trigger-per-second} on
 * average over ten seconds. There is no thread; the end of a flood is noticed by the next call.
 * Thread-safe.
 */
final class FloodDetector {

    private static final int WINDOW_SECONDS = 10;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final TimeSource time;
    private final long[] second = new long[WINDOW_SECONDS];
    private final int[] attempts = new int[WINDOW_SECONDS];
    private boolean active;
    /** Moves only when an attempt is recorded, so asking for the status cannot prolong a flood. */
    private long lastAbove;
    private TokenBucket allowance;

    FloodDetector(TimeSource time) {
        this.time = time;
        Arrays.fill(second, Long.MIN_VALUE);
    }

    /** @return true if the flood just ended */
    synchronized boolean expire(NetworkSettings.Adaptive cfg) {
        if (active && time.nanoTime() - lastAbove >= cfg.calmSeconds() * NANOS_PER_SECOND) {
            active = false;
            allowance = null;
            return true;
        }
        return false;
    }

    /** @return true if this attempt started a flood */
    synchronized boolean record(NetworkSettings.Adaptive cfg) {
        long now = time.nanoTime();
        long current = Math.floorDiv(now, NANOS_PER_SECOND);
        int slot = (int) Math.floorMod(current, (long) WINDOW_SECONDS);
        if (second[slot] != current) {
            second[slot] = current;
            attempts[slot] = 0;
        }
        attempts[slot]++;
        long total = 0;
        for (int i = 0; i < WINDOW_SECONDS; i++) {
            if (second[i] > current - WINDOW_SECONDS) {
                total += attempts[i];
            }
        }
        if (total <= (long) cfg.triggerPerSecond() * WINDOW_SECONDS) {
            return false;
        }
        lastAbove = now;
        if (active) {
            return false;
        }
        active = true;
        allowance = new TokenBucket(cfg.unknownPerSecond(), cfg.unknownPerSecond(), time);
        return true;
    }

    synchronized boolean active() {
        return active;
    }

    /** Takes one login from the shared allowance; always true when there is no flood. */
    synchronized boolean tryAllow() {
        return allowance == null || allowance.tryAcquire();
    }
}
