package io.github.intruder_tech.blackdoor.physics.replay;

import io.github.intruder_tech.blackdoor.protocol.world.BlockShapes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** The blocks a trace has shown the client, and vertical collision against them. */
public final class TraceWorld {

    /** Half the player's width. */
    static final double HALF_WIDTH = 0.3;
    private static final double EPSILON = 1e-7;

    /** The part of a vertical move that is possible, and whether something stopped it. */
    public record Clip(double dy, boolean collided) {
    }

    private record Column(Map<Integer, int[]> sections) {
    }

    private final Map<Long, Column> columns = new HashMap<>();

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    private static int number(Map<String, Object> event, String field) {
        return ((Double) event.get(field)).intValue();
    }

    public void apply(Map<String, Object> event) {
        switch (String.valueOf(event.get("k"))) {
            case "chunk" -> chunk(event);
            case "unload" -> columns.remove(key(number(event, "x"), number(event, "z")));
            case "world" -> columns.clear();
            case "block" -> set(number(event, "x"), number(event, "y"), number(event, "z"), number(event, "id"));
            default -> {
            }
        }
    }

    private void chunk(Map<String, Object> event) {
        int minSection = number(event, "minSection");
        Column column = new Column(new HashMap<>());
        for (Object entry : (List<?>) event.get("sections")) {
            List<?> section = (List<?>) entry;
            int[] blocks = new int[4096];
            List<?> runs = (List<?>) section.get(1);
            int at = 0;
            for (int i = 0; i < runs.size(); i += 2) {
                int count = ((Double) runs.get(i)).intValue();
                Arrays.fill(blocks, at, at + count, ((Double) runs.get(i + 1)).intValue());
                at += count;
            }
            column.sections().put(minSection + ((Double) section.get(0)).intValue(), blocks);
        }
        columns.put(key(number(event, "x"), number(event, "z")), column);
    }

    private void set(int x, int y, int z, int id) {
        Column column = columns.get(key(x >> 4, z >> 4));
        if (column != null) {
            column.sections().computeIfAbsent(y >> 4, s -> new int[4096])[index(x, y, z)] = id;
        }
    }

    private static int index(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    /** Empty when the chunk is unknown. */
    public OptionalInt block(int x, int y, int z) {
        Column column = columns.get(key(x >> 4, z >> 4));
        if (column == null) {
            return OptionalInt.empty();
        }
        int[] blocks = column.sections().get(y >> 4);
        return OptionalInt.of(blocks == null ? 0 : blocks[index(x, y, z)]);
    }

    /** True if every block the player's box at this place overlaps is known and ordinary. */
    public boolean plain(BlockShapes shapes, double x, double y, double z, double height) {
        return clip(shapes, x, y, z, height, 0).isPresent();
    }

    /**
     * Moves the player's box vertically by {@code dy} from a position. Empty when a block it
     * passes through is unknown or special, or its chunk is unknown.
     */
    public Optional<Clip> clip(BlockShapes shapes, double x, double y, double z, double height, double dy) {
        double minX = x - HALF_WIDTH;
        double maxX = x + HALF_WIDTH;
        double minZ = z - HALF_WIDTH;
        double maxZ = z + HALF_WIDTH;
        double low = Math.min(y, y + dy);
        double high = Math.max(y, y + dy) + height;
        double result = dy;
        boolean collided = false;
        // One layer further down, because fences and walls reach into the block above.
        for (int by = floor(low - 0.001) - 1; by <= floor(high); by++) {
            for (int bx = floor(minX + EPSILON); bx <= floor(maxX - EPSILON); bx++) {
                for (int bz = floor(minZ + EPSILON); bz <= floor(maxZ - EPSILON); bz++) {
                    OptionalInt id = block(bx, by, bz);
                    if (id.isEmpty()) {
                        return Optional.empty();
                    }
                    Optional<List<BlockShapes.Box>> boxes = shapes.boxes(id.getAsInt());
                    boolean touched = by + 1 > low - 0.001 && by < high;
                    if (boxes.isEmpty() || (touched && shapes.special(id.getAsInt()))) {
                        return Optional.empty();
                    }
                    for (BlockShapes.Box box : boxes.get()) {
                        if (bx + box.maxX() <= minX + EPSILON || bx + box.minX() >= maxX - EPSILON
                                || bz + box.maxZ() <= minZ + EPSILON || bz + box.minZ() >= maxZ - EPSILON) {
                            continue;
                        }
                        double top = by + box.maxY();
                        double bottom = by + box.minY();
                        if (result < 0 && top <= y + EPSILON && top - y > result) {
                            result = top - y;
                            collided = true;
                        } else if (result > 0 && bottom >= y + height - EPSILON && bottom - y - height < result) {
                            result = bottom - y - height;
                            collided = true;
                        }
                    }
                }
            }
        }
        return Optional.of(new Clip(result, collided));
    }

    /** The highest box top above {@code from} and up to {@code to} under the player's box at this place. */
    public OptionalDouble top(BlockShapes shapes, double x, double z, double from, double to) {
        double highest = Double.NEGATIVE_INFINITY;
        for (int by = floor(from) - 1; by <= floor(to); by++) {
            for (int bx = floor(x - HALF_WIDTH + EPSILON); bx <= floor(x + HALF_WIDTH - EPSILON); bx++) {
                for (int bz = floor(z - HALF_WIDTH + EPSILON); bz <= floor(z + HALF_WIDTH - EPSILON); bz++) {
                    OptionalInt id = block(bx, by, bz);
                    if (id.isEmpty()) {
                        return OptionalDouble.empty();
                    }
                    for (BlockShapes.Box box : shapes.boxes(id.getAsInt()).orElse(List.of())) {
                        double top = by + box.maxY();
                        boolean under = bx + box.maxX() > x - HALF_WIDTH + EPSILON
                                && bx + box.minX() < x + HALF_WIDTH - EPSILON
                                && bz + box.maxZ() > z - HALF_WIDTH + EPSILON
                                && bz + box.minZ() < z + HALF_WIDTH - EPSILON;
                        if (under && top > from + EPSILON && top <= to + EPSILON) {
                            highest = Math.max(highest, top);
                        }
                    }
                }
            }
        }
        return highest == Double.NEGATIVE_INFINITY ? OptionalDouble.empty() : OptionalDouble.of(highest);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
