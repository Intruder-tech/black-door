package io.github.intruder_tech.blackdoor.state;

/**
 * Server-applied velocities for the player's own entity, each tied to the transaction sequence
 * that follows it: once the client acknowledges that sequence, the velocity has certainly been
 * received. Until then the physics engine (milestone 2.4) must accept movement both with and
 * without it. Bounded ring; oldest entries are dropped when full.
 */
public final class VelocityQueue {

    private final double[][] entries;
    private final long[] sequences;
    private int head;
    private int size;
    private long applied;
    private double[] lastApplied;

    public VelocityQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        entries = new double[capacity][];
        sequences = new long[capacity];
    }

    public synchronized void add(double x, double y, double z, long requiredSequence) {
        if (size == entries.length) {
            head = (head + 1) % entries.length;
            size--;
        }
        int slot = (head + size) % entries.length;
        entries[slot] = new double[]{x, y, z};
        sequences[slot] = requiredSequence;
        size++;
    }

    /** Marks every velocity whose sequence has been acknowledged as received by the client. */
    public synchronized void acknowledgedUpTo(long sequence) {
        while (size > 0 && sequences[head] <= sequence) {
            lastApplied = entries[head];
            entries[head] = null;
            head = (head + 1) % entries.length;
            size--;
            applied++;
        }
    }

    public synchronized int pending() {
        return size;
    }

    public synchronized long applied() {
        return applied;
    }

    /** Last velocity the client has confirmed receiving, or null. */
    public synchronized double[] lastApplied() {
        return lastApplied == null ? null : lastApplied.clone();
    }
}
