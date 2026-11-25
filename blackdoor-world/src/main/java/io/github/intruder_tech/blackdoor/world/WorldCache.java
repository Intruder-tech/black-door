package io.github.intruder_tech.blackdoor.world;

import io.github.intruder_tech.blackdoor.protocol.world.BlockSource;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeClass;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-wide collision cache shared by all players (ADR-017). Keyed by world namespace and chunk
 * position; reference-counted by the players who currently have the chunk loaded; bounded by a
 * memory budget. When the budget is exhausted new chunks are not stored and lookups for them
 * return null ("unknown"), which physics turns into "no verdict".
 *
 * <p>Thread-safety: many network threads read and write. Entries are mutated under their own
 * monitor; readers see immutable {@link SectionShapes} through a volatile array.
 */
public final class WorldCache {

    record Key(String world, int x, int z) {
    }

    static final class Entry {
        volatile SectionShapes[] sections;
        final int minSectionY;
        int refs;
        long bytes;

        Entry(SectionShapes[] sections, int minSectionY, long bytes) {
            this.sections = sections;
            this.minSectionY = minSectionY;
            this.bytes = bytes;
        }
    }

    private final Map<Key, Entry> chunks = new ConcurrentHashMap<>();
    private final long budgetBytes;
    private final AtomicLong usedBytes = new AtomicLong();
    private final AtomicLong refused = new AtomicLong();
    private final AtomicLong conversions = new AtomicLong();

    public WorldCache(long budgetBytes) {
        if (budgetBytes < 1) {
            throw new IllegalArgumentException("budget must be positive");
        }
        this.budgetBytes = budgetBytes;
    }

    /**
     * A player received a chunk: take a reference, converting the packet only if no other player
     * already holds this chunk (in which case the entry is kept current by block changes).
     *
     * @return true if the chunk is now tracked for this player
     */
    public boolean acquire(String world, int x, int z, int minSectionY, BlockSource source, ShapeRegistry registry) {
        Key key = new Key(world, x, z);
        Entry entry = chunks.get(key);
        if (entry == null) {
            SectionShapes[] sections = new SectionShapes[source.sections()];
            long bytes = 0;
            for (int i = 0; i < sections.length; i++) {
                sections[i] = SectionShapes.of(source, i, registry);
                bytes += sections[i].bytes();
            }
            conversions.incrementAndGet();
            if (usedBytes.get() + bytes > budgetBytes) {
                refused.incrementAndGet();
                return false;
            }
            Entry fresh = new Entry(sections, minSectionY, bytes);
            entry = chunks.putIfAbsent(key, fresh);
            if (entry == null) {
                entry = fresh;
                usedBytes.addAndGet(bytes);
            }
        }
        synchronized (entry) {
            if (chunks.get(key) != entry) {
                return acquire(world, x, z, minSectionY, source, registry);
            }
            entry.refs++;
        }
        return true;
    }

    /** A player forgot a chunk (unload, world change, disconnect). */
    public void release(String world, int x, int z) {
        Key key = new Key(world, x, z);
        Entry entry = chunks.get(key);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (--entry.refs <= 0 && chunks.remove(key, entry)) {
                usedBytes.addAndGet(-entry.bytes);
            }
        }
    }

    /** Applies a block change; returns the previous class, or null if the chunk is not tracked. */
    public ShapeClass change(String world, int x, int y, int z, ShapeClass value) {
        Entry entry = chunks.get(new Key(world, x >> 4, z >> 4));
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            int s = (y >> 4) - entry.minSectionY;
            SectionShapes[] sections = entry.sections;
            if (s < 0 || s >= sections.length) {
                return null;
            }
            SectionShapes old = sections[s];
            ShapeClass previous = old.at(x & 15, y & 15, z & 15);
            SectionShapes updated = old.with(x & 15, y & 15, z & 15, value);
            if (updated != old) {
                SectionShapes[] copy = sections.clone();
                copy[s] = updated;
                long delta = updated.bytes() - old.bytes();
                entry.bytes += delta;
                usedBytes.addAndGet(delta);
                entry.sections = copy;
            }
            return previous;
        }
    }

    /** Collision class at a block position, or null if unknown (not loaded or outside the world). */
    public ShapeClass shapeAt(String world, int x, int y, int z) {
        Entry entry = chunks.get(new Key(world, x >> 4, z >> 4));
        if (entry == null) {
            return null;
        }
        SectionShapes[] sections = entry.sections;
        int s = (y >> 4) - entry.minSectionY;
        if (s < 0 || s >= sections.length) {
            return null;
        }
        return sections[s].at(x & 15, y & 15, z & 15);
    }

    public Stats stats() {
        return new Stats(chunks.size(), usedBytes.get(), budgetBytes, refused.get(), conversions.get());
    }

    /** Cache counters: tracked chunks, bytes used, budget, chunks refused for budget, packet conversions. */
    public record Stats(int chunks, long usedBytes, long budgetBytes, long refused, long conversions) {
    }
}
