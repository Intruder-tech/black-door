package io.github.intruder_tech.blackdoor.benchmarks.worldview;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Candidate in-memory layouts for one 16x16x16 section, with byte-size estimates (compressed oops). */
final class Layouts {

    private Layouts() {
    }

    /** Layout A: copy of the block-state palette and packed data as sent to the client. */
    record FullSection(int[] palette, long[] data) {
        long bytes() {
            return 24 + intArray(palette.length) + (data == null ? 0 : longArray(data.length));
        }
    }

    /** Layout B: collision-shape palette and packed indices. Uniform sections are shared singletons. */
    record ShapeSection(int[] palette, long[] packed) {
        boolean uniform() {
            return packed == null;
        }

        long bytes() {
            return 24 + intArray(palette.length) + (packed == null ? 0 : longArray(packed.length));
        }
    }

    /** Content key for de-duplicating identical shape sections (layout C). */
    record Key(ShapeSection section) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Arrays.equals(section.palette, k.section.palette)
                    && Arrays.equals(section.packed, k.section.packed);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(section.palette) + Arrays.hashCode(section.packed);
        }
    }

    static long intArray(int n) {
        return align(16 + 4L * n);
    }

    static long longArray(int n) {
        return 16 + 8L * n;
    }

    static long align(long n) {
        return (n + 7) & ~7L;
    }

    static FullSection full(RegionReader.Section s, CollisionModel model) {
        int[] palette = new int[s.palette().length];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = model.blockStateId(s.palette()[i]);
        }
        if (palette.length == 1) {
            return new FullSection(palette, null);
        }
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
        return new FullSection(palette, pack(s.indices(), bits));
    }

    /**
     * @param shapeOfPaletteEntry shape id for each entry of the section's block palette
     * @param uniformPool         shared singletons for uniform sections, keyed by shape id
     */
    static ShapeSection shape(int[] indices, int[] shapeOfPaletteEntry, Map<Integer, ShapeSection> uniformPool) {
        Map<Integer, Integer> local = new LinkedHashMap<>();
        for (int shape : shapeOfPaletteEntry) {
            local.putIfAbsent(shape, local.size());
        }
        if (local.size() == 1) {
            int only = shapeOfPaletteEntry[0];
            return uniformPool.computeIfAbsent(only, id -> new ShapeSection(new int[]{id}, null));
        }
        int[] palette = local.keySet().stream().mapToInt(Integer::intValue).toArray();
        int[] remap = new int[shapeOfPaletteEntry.length];
        for (int i = 0; i < remap.length; i++) {
            remap[i] = local.get(shapeOfPaletteEntry[i]);
        }
        int[] shapeIndices = new int[4096];
        boolean used1 = false;
        for (int i = 0; i < 4096; i++) {
            shapeIndices[i] = remap[indices[i]];
            used1 |= shapeIndices[i] != shapeIndices[0];
        }
        if (!used1) {
            int only = palette[shapeIndices[0]];
            return uniformPool.computeIfAbsent(only, id -> new ShapeSection(new int[]{id}, null));
        }
        int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
        return new ShapeSection(palette, pack(shapeIndices, bits));
    }

    static long[] pack(int[] values, int bits) {
        int perLong = 64 / bits;
        long[] out = new long[(4096 + perLong - 1) / perLong];
        for (int i = 0; i < 4096; i++) {
            out[i / perLong] |= ((long) values[i]) << ((i % perLong) * bits);
        }
        return out;
    }

    static Map<Integer, ShapeSection> newPool() {
        return new HashMap<>();
    }
}
