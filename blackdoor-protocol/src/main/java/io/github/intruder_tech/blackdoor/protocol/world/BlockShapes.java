package io.github.intruder_tech.blackdoor.protocol.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Exact collision boxes per global block-state id. An id is <em>unknown</em> until it is put, and
 * stays unknown when the shape cannot be told; unknown means physics gives no verdict. An empty
 * list is a known block without collision. Safe for one writer and many readers.
 */
public final class BlockShapes {

    public static final int CAPACITY = 1 << 17;

    /** One box in block-local coordinates; some reach above 1 (fences). */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    /** {@code boxes} is null for an unknown shape. */
    private record Entry(List<Box> boxes) {
    }

    private static final Entry UNKNOWN = new Entry(null);

    private final AtomicReferenceArray<Entry> entries = new AtomicReferenceArray<>(CAPACITY);

    public void put(int id, List<Box> boxes) {
        set(id, new Entry(List.copyOf(boxes)));
    }

    public void putUnknown(int id) {
        set(id, UNKNOWN);
    }

    private void set(int id, Entry entry) {
        if (id >= 0 && id < CAPACITY) {
            entries.set(id, entry);
        }
    }

    public Optional<List<Box>> boxes(int id) {
        Entry entry = id >= 0 && id < CAPACITY ? entries.get(id) : null;
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.boxes());
    }

    /** Number of ids with a known shape. */
    public int known() {
        int known = 0;
        for (int id = 0; id < CAPACITY; id++) {
            Entry entry = entries.get(id);
            if (entry != null && entry.boxes() != null) {
                known++;
            }
        }
        return known;
    }

    /** A {@code server} line, then {@code id -}, {@code id ?} or {@code id x1,y1,z1,x2,y2,z2;...}. */
    public void write(Writer out, String server) throws IOException {
        out.write("server " + server + "\n");
        for (int id = 0; id < CAPACITY; id++) {
            Entry entry = entries.get(id);
            if (entry == null) {
                continue;
            }
            StringBuilder line = new StringBuilder().append(id).append(' ');
            if (entry.boxes() == null) {
                line.append('?');
            } else if (entry.boxes().isEmpty()) {
                line.append('-');
            } else {
                for (int i = 0; i < entry.boxes().size(); i++) {
                    Box b = entry.boxes().get(i);
                    line.append(i == 0 ? "" : ";").append(b.minX()).append(',').append(b.minY()).append(',')
                            .append(b.minZ()).append(',').append(b.maxX()).append(',').append(b.maxY()).append(',')
                            .append(b.maxZ());
                }
            }
            out.write(line.append('\n').toString());
        }
    }

    public static BlockShapes read(Reader in) throws IOException {
        BlockShapes shapes = new BlockShapes();
        BufferedReader lines = new BufferedReader(in);
        String line = lines.readLine();
        if (line == null || !line.startsWith("server ")) {
            throw new IOException("not a block shape table");
        }
        while ((line = lines.readLine()) != null) {
            int space = line.indexOf(' ');
            int id = Integer.parseInt(line.substring(0, space));
            String value = line.substring(space + 1);
            if (value.equals("?")) {
                shapes.putUnknown(id);
                continue;
            }
            List<Box> boxes = new ArrayList<>();
            if (!value.equals("-")) {
                for (String box : value.split(";")) {
                    String[] v = box.split(",");
                    boxes.add(new Box(Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2]),
                            Double.parseDouble(v[3]), Double.parseDouble(v[4]), Double.parseDouble(v[5])));
                }
            }
            shapes.put(id, boxes);
        }
        return shapes;
    }
}
