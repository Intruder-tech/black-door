package io.github.intruder_tech.blackdoor.packets;

import java.util.concurrent.TimeUnit;

/**
 * Whether the server last told this client it is in creative mode. Actions sent before the client
 * learned of a switch away from creative are still arriving for a short time, so they are tolerated.
 */
final class GameModeState {

    private static final long GRACE_NANOS = TimeUnit.SECONDS.toNanos(5);

    private boolean creative;
    private boolean left;
    private long leftAtNanos;

    synchronized void update(boolean nowCreative, long nanos) {
        if (creative && !nowCreative) {
            left = true;
            leftAtNanos = nanos;
        }
        creative = nowCreative;
    }

    synchronized boolean mayUseCreative(long nanos) {
        return creative || (left && nanos - leftAtNanos <= GRACE_NANOS);
    }
}
