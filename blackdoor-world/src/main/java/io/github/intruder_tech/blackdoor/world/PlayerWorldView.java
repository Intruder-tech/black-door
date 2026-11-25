package io.github.intruder_tech.blackdoor.world;

import io.github.intruder_tech.blackdoor.protocol.world.BlockSource;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeClass;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeRegistry;

import java.util.HashSet;
import java.util.Set;

/**
 * One player's window onto the shared {@link WorldCache}: which chunks the client has, in which
 * world, plus block positions changed by the server that this client has not acknowledged yet
 * (physics treats those as uncertain). Single writer: the player's network thread.
 */
public final class PlayerWorldView {

    public static final int MAX_CHUNKS = 8192;
    public static final int MAX_PENDING = 256;

    private final WorldCache cache;
    private final String privateNamespace;
    private final Set<Long> loaded = new HashSet<>();
    private final int[] px = new int[MAX_PENDING];
    private final int[] py = new int[MAX_PENDING];
    private final int[] pz = new int[MAX_PENDING];
    private final long[] pseq = new long[MAX_PENDING];
    private int pending;
    private long uncertainUntil;
    private String world;
    private long untracked;

    public PlayerWorldView(WorldCache cache, String privateNamespace) {
        this.cache = cache;
        this.privateNamespace = privateNamespace;
        this.world = privateNamespace;
    }

    /** Login or respawn: the client drops all chunks. A null name keeps data private to this player. */
    public void changeWorld(String worldName) {
        releaseAll();
        world = worldName == null || worldName.isEmpty() ? privateNamespace : "w:" + worldName;
    }

    public void chunk(int x, int z, int minSectionY, BlockSource source, ShapeRegistry registry) {
        long key = key(x, z);
        if (loaded.contains(key)) {
            return;
        }
        if (loaded.size() >= MAX_CHUNKS || !cache.acquire(world, x, z, minSectionY, source, registry)) {
            untracked++;
            return;
        }
        loaded.add(key);
    }

    public void unload(int x, int z) {
        if (loaded.remove(key(x, z))) {
            cache.release(world, x, z);
        }
    }

    /** Records a server block change; uncertain for this player until {@code requiredSequence} is acknowledged. */
    public void blockChange(int x, int y, int z, ShapeClass value, long requiredSequence) {
        if (!loaded.contains(key(x >> 4, z >> 4))) {
            return;
        }
        cache.change(world, x, y, z, value);
        if (pending == MAX_PENDING) {
            uncertainUntil = Math.max(uncertainUntil, requiredSequence);
            return;
        }
        px[pending] = x;
        py[pending] = y;
        pz[pending] = z;
        pseq[pending] = requiredSequence;
        pending++;
    }

    /** The client acknowledged every transaction up to {@code sequence}. */
    public void acknowledged(long sequence) {
        int kept = 0;
        for (int i = 0; i < pending; i++) {
            if (pseq[i] > sequence) {
                px[kept] = px[i];
                py[kept] = py[i];
                pz[kept] = pz[i];
                pseq[kept] = pseq[i];
                kept++;
            }
        }
        pending = kept;
        if (uncertainUntil <= sequence) {
            uncertainUntil = 0;
        }
    }

    /** Collision class at a position as the client should currently see it, or null if unknown. */
    public ShapeClass shapeAt(int x, int y, int z) {
        if (!loaded.contains(key(x >> 4, z >> 4))) {
            return null;
        }
        return cache.shapeAt(world, x, y, z);
    }

    /** True if an unacknowledged block change lies within {@code radius} blocks (or tracking overflowed). */
    public boolean uncertainNear(int x, int y, int z, int radius) {
        if (uncertainUntil > 0) {
            return true;
        }
        for (int i = 0; i < pending; i++) {
            if (Math.abs(px[i] - x) <= radius && Math.abs(py[i] - y) <= radius && Math.abs(pz[i] - z) <= radius) {
                return true;
            }
        }
        return false;
    }

    public void releaseAll() {
        for (long k : loaded) {
            cache.release(world, (int) (k >> 32), (int) k);
        }
        loaded.clear();
        pending = 0;
        uncertainUntil = 0;
    }

    public int loadedChunks() {
        return loaded.size();
    }

    public int pendingChanges() {
        return pending;
    }

    public long untracked() {
        return untracked;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
