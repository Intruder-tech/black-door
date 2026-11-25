package io.github.intruder_tech.blackdoor.state;

import java.util.HashMap;
import java.util.Map;

/**
 * Positions of the entities the client has been told about (needed later for reach and hitbox
 * checks). Bounded: when full, new entities are not tracked and counted as untracked, so any
 * check that needs them returns "no verdict" instead of guessing. Single writer: network thread.
 */
public final class EntityTracker {

    private final int capacity;
    private final Map<Integer, double[]> positions = new HashMap<>();
    private volatile int size;
    private long untracked;

    public EntityTracker(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
    }

    public void spawn(int entityId, double x, double y, double z) {
        double[] p = positions.get(entityId);
        if (p == null) {
            if (positions.size() >= capacity) {
                untracked++;
                return;
            }
            positions.put(entityId, new double[]{x, y, z});
            size = positions.size();
        } else {
            p[0] = x;
            p[1] = y;
            p[2] = z;
        }
    }

    public void move(int entityId, double dx, double dy, double dz) {
        double[] p = positions.get(entityId);
        if (p != null) {
            p[0] += dx;
            p[1] += dy;
            p[2] += dz;
        }
    }

    public void teleport(int entityId, double x, double y, double z) {
        double[] p = positions.get(entityId);
        if (p != null) {
            p[0] = x;
            p[1] = y;
            p[2] = z;
        }
    }

    public void remove(int entityId) {
        positions.remove(entityId);
        size = positions.size();
    }

    /** Copy of the position, or null if the entity is unknown. Network thread only. */
    public double[] position(int entityId) {
        double[] p = positions.get(entityId);
        return p == null ? null : p.clone();
    }

    public int size() {
        return size;
    }

    public long untracked() {
        return untracked;
    }
}
