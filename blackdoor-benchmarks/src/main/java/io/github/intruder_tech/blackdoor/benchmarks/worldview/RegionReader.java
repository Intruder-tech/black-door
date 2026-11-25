package io.github.intruder_tech.blackdoor.benchmarks.worldview;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Reads block-state sections from Anvil region files (1.18+ layout). Spike only. */
final class RegionReader {

    /** One 16x16x16 section: palette entries (name + sorted properties) and 4096 palette indices. */
    record Section(String[] palette, int[] indices) {
    }


    private RegionReader() {
    }

    static List<List<Section>> readAll(Path regionDir) throws IOException {
        List<List<Section>> chunks = new ArrayList<>();
        try (Stream<Path> files = Files.list(regionDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".mca")).sorted().toList()) {
                readRegion(file, chunks);
            }
        }
        return chunks;
    }

    private static void readRegion(Path file, List<List<Section>> out) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            if (raf.length() < 8192) {
                return;
            }
            int[] offsets = new int[1024];
            for (int i = 0; i < 1024; i++) {
                offsets[i] = raf.readInt();
            }
            for (int entry : offsets) {
                int sector = entry >>> 8;
                if (sector == 0) {
                    continue;
                }
                raf.seek((long) sector * 4096);
                int length = raf.readInt();
                int compression = raf.readUnsignedByte();
                if (length <= 1 || length > 16 * 1024 * 1024 || (compression & 0x80) != 0) {
                    continue;
                }
                byte[] data = new byte[length - 1];
                raf.readFully(data);
                InputStream raw = new ByteArrayInputStream(data);
                InputStream in = switch (compression) {
                    case 1 -> new GZIPInputStream(raw);
                    case 2 -> new InflaterInputStream(raw);
                    case 3 -> raw;
                    default -> null;
                };
                if (in == null) {
                    continue;
                }
                List<Section> sections = parseChunk(NbtReader.readRoot(new DataInputStream(in)));
                if (!sections.isEmpty()) {
                    out.add(sections);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Section> parseChunk(Map<String, Object> root) {
        List<Section> result = new ArrayList<>();
        Object status = root.get("Status");
        if (status != null && !String.valueOf(status).endsWith("full")) {
            return result;
        }
        Object sections = root.get("sections");
        if (!(sections instanceof List<?> list)) {
            return result;
        }
        for (Object o : list) {
            Map<String, Object> section = (Map<String, Object>) o;
            Object states = section.get("block_states");
            if (!(states instanceof Map<?, ?> bs)) {
                continue;
            }
            List<Object> palette = (List<Object>) bs.get("palette");
            if (palette == null || palette.isEmpty()) {
                continue;
            }
            String[] keys = new String[palette.size()];
            for (int i = 0; i < keys.length; i++) {
                Object raw = palette.get(i);
                if (!(raw instanceof Map<?, ?> entry)) {
                    keys[i] = String.valueOf(raw);
                    continue;
                }
                // 1.18-1.21: Name/Properties. 26.x: id/properties, or a plain string for default states.
                String name = String.valueOf(entry.get("Name") != null ? entry.get("Name") : entry.get("id"));
                Object props = entry.get("Properties") != null ? entry.get("Properties") : entry.get("properties");
                keys[i] = props instanceof Map<?, ?> p ? name + new TreeMap<>(p) : name;
            }
            result.add(new Section(keys, decode((long[]) bs.get("data"), keys.length)));
        }
        return result;
    }

    /** 1.16+ packing: values never span two longs. */
    static int[] decode(long[] data, int paletteSize) {
        int[] out = new int[4096];
        if (data == null || paletteSize <= 1) {
            return out;
        }
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1;
        for (int i = 0; i < 4096; i++) {
            int longIndex = i / perLong;
            if (longIndex >= data.length) {
                break;
            }
            out[i] = (int) ((data[longIndex] >>> ((i % perLong) * bits)) & mask);
        }
        return out;
    }
}
