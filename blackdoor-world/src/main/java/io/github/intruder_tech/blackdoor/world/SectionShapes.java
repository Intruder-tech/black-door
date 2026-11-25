package io.github.intruder_tech.blackdoor.world;

import io.github.intruder_tech.blackdoor.protocol.world.BlockSource;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeClass;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeRegistry;

/**
 * Immutable collision classes of one 16x16x16 section (layout B from BENCHMARK_REPORT.md §1):
 * uniform sections are shared singletons; others store a small class palette and packed indices.
 */
public final class SectionShapes {

    private static final ShapeClass[] CLASSES = ShapeClass.values();
    private static final SectionShapes[] UNIFORM = new SectionShapes[CLASSES.length];

    static {
        for (ShapeClass c : CLASSES) {
            UNIFORM[c.ordinal()] = new SectionShapes(new byte[]{(byte) c.ordinal()}, null, 0);
        }
    }

    private final byte[] palette;
    private final long[] packed;
    private final int bits;

    private SectionShapes(byte[] palette, long[] packed, int bits) {
        this.palette = palette;
        this.packed = packed;
        this.bits = bits;
    }

    public static SectionShapes uniform(ShapeClass c) {
        return UNIFORM[c.ordinal()];
    }

    /** Converts one section of a chunk packet. */
    public static SectionShapes of(BlockSource source, int section, ShapeRegistry registry) {
        if (source.emptySection(section)) {
            return uniform(ShapeClass.EMPTY);
        }
        byte[] classes = new byte[4096];
        int seen = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    ShapeClass c = registry.classify(source.blockId(section, x, y, z));
                    classes[index(x, y, z)] = (byte) c.ordinal();
                    seen |= 1 << c.ordinal();
                }
            }
        }
        return fromClasses(classes, seen);
    }

    static SectionShapes fromClasses(byte[] classes, int seenMask) {
        int distinct = Integer.bitCount(seenMask);
        if (distinct == 1) {
            return UNIFORM[Integer.numberOfTrailingZeros(seenMask)];
        }
        byte[] palette = new byte[distinct];
        int[] localOf = new int[CLASSES.length];
        int next = 0;
        for (int c = 0; c < CLASSES.length; c++) {
            if ((seenMask & (1 << c)) != 0) {
                palette[next] = (byte) c;
                localOf[c] = next++;
            }
        }
        int bits = 32 - Integer.numberOfLeadingZeros(distinct - 1);
        int perLong = 64 / bits;
        long[] packed = new long[(4096 + perLong - 1) / perLong];
        for (int i = 0; i < 4096; i++) {
            packed[i / perLong] |= ((long) localOf[classes[i]]) << ((i % perLong) * bits);
        }
        return new SectionShapes(palette, packed, bits);
    }

    public ShapeClass at(int x, int y, int z) {
        if (packed == null) {
            return CLASSES[palette[0]];
        }
        int i = index(x, y, z);
        int perLong = 64 / bits;
        int local = (int) ((packed[i / perLong] >>> ((i % perLong) * bits)) & ((1L << bits) - 1));
        return CLASSES[palette[local]];
    }

    /** Copy with one block changed (copy-on-write; sections stay immutable). */
    public SectionShapes with(int x, int y, int z, ShapeClass value) {
        if (at(x, y, z) == value) {
            return this;
        }
        byte[] classes = new byte[4096];
        int seen = 0;
        for (int yy = 0; yy < 16; yy++) {
            for (int zz = 0; zz < 16; zz++) {
                for (int xx = 0; xx < 16; xx++) {
                    ShapeClass c = (xx == x && yy == y && zz == z) ? value : at(xx, yy, zz);
                    classes[index(xx, yy, zz)] = (byte) c.ordinal();
                    seen |= 1 << c.ordinal();
                }
            }
        }
        return fromClasses(classes, seen);
    }

    public boolean isUniform() {
        return packed == null;
    }

    /** Approximate retained bytes (0 for shared uniform singletons). */
    public long bytes() {
        return packed == null ? 0 : 24 + 16 + ((palette.length + 7) & ~7) + 16 + 8L * packed.length;
    }

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }
}
