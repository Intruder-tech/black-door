package io.github.intruder_tech.blackdoor.state;

/**
 * Teleport ids the server has sent and the client has not confirmed yet. Fixed capacity; when
 * full, the oldest id is forgotten (clients that never confirm, such as 1.8 clients, cannot
 * grow memory and do not cause findings).
 *
 * <p>Synchronized because outbound and inbound handling are not guaranteed to share a thread on
 * every platform [verify]; the lock is uncontended in practice.
 */
public final class PendingTeleports {

    public enum Result { CONFIRMED_IN_ORDER, CONFIRMED_OUT_OF_ORDER, UNKNOWN }

    private final int[] ids;
    private int head;
    private int size;
    private long overflowed;

    public PendingTeleports(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.ids = new int[capacity];
    }

    public synchronized void sent(int teleportId) {
        if (size == ids.length) {
            head = (head + 1) % ids.length;
            size--;
            overflowed++;
        }
        ids[(head + size) % ids.length] = teleportId;
        size++;
    }

    /** Records a confirmation; removes the id and every older id. */
    public synchronized Result confirm(int teleportId) {
        for (int i = 0; i < size; i++) {
            if (ids[(head + i) % ids.length] == teleportId) {
                head = (head + i + 1) % ids.length;
                size -= i + 1;
                return i == 0 ? Result.CONFIRMED_IN_ORDER : Result.CONFIRMED_OUT_OF_ORDER;
            }
        }
        return Result.UNKNOWN;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized long overflowed() {
        return overflowed;
    }
}
