package io.github.intruder_tech.blackdoor.benchmarks.worldview;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Milestone 2.3 spike (OD-3): how much memory does a per-player world view cost, for three layouts?
 *  A = copy of block-state palettes (what the client received), per player
 *  B = collision-shape palettes, per player (uniform sections shared)
 *  C = collision-shape sections de-duplicated across all players (shared pool + per-player references)
 * Prints a markdown report. Spike code only; never shipped.
 */
public final class WorldViewSpike {

    static final int VIEW_CHUNKS = 21 * 21;   // view distance 10
    static final int SECTIONS_PER_CHUNK = 24; // overworld -64..319

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        long t0 = System.nanoTime();
        List<List<RegionReader.Section>> chunks = RegionReader.readAll(dir);
        System.out.printf(Locale.ROOT, "Loaded %d full chunks from %s in %.1f s%n%n", chunks.size(), dir.getFileName(),
                (System.nanoTime() - t0) / 1e9);
        CollisionModel model = new CollisionModel();
        Map<Integer, Layouts.ShapeSection> pool = Layouts.newPool();

        long sections = 0;
        long uniformShape = 0;
        long uniformBlocks = 0;
        long paletteSum = 0;
        long shapeSum = 0;
        long bytesA = 0;
        long bytesB = 0;
        List<Layouts.ShapeSection[]> shapeChunks = new ArrayList<>();
        for (List<RegionReader.Section> chunk : chunks) {
            Layouts.ShapeSection[] row = new Layouts.ShapeSection[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                RegionReader.Section s = chunk.get(i);
                Layouts.FullSection a = Layouts.full(s, model);
                Layouts.ShapeSection b = Layouts.shape(s.indices(), shapes(s, model), pool);
                row[i] = b;
                sections++;
                paletteSum += s.palette().length;
                if (s.palette().length == 1) {
                    uniformBlocks++;
                }
                bytesA += a.bytes();
                if (b.uniform()) {
                    uniformShape++;
                } else {
                    shapeSum += b.palette().length;
                    bytesB += b.bytes();
                }
            }
            shapeChunks.add(row);
        }
        double perChunkA = (double) bytesA / chunks.size() + 16 + 4.0 * SECTIONS_PER_CHUNK;
        double perChunkB = (double) bytesB / chunks.size() + 16 + 4.0 * SECTIONS_PER_CHUNK;
        long nonUniform = sections - uniformShape;
        System.out.println("## Section statistics (real terrain)");
        System.out.println();
        System.out.println("| Metric | Value |");
        System.out.println("|---|---|");
        row("Chunks / sections", chunks.size() + " / " + sections);
        row("Avg block-state palette size", fmt(paletteSum / (double) sections));
        row("Sections with a single block state", pct(uniformBlocks, sections));
        row("Sections with a single collision shape", pct(uniformShape, sections));
        row("Avg shapes in non-uniform sections", fmt(nonUniform == 0 ? 0 : shapeSum / (double) nonUniform));
        row("Distinct collision shapes seen", String.valueOf(model.distinctShapes()));
        row("Distinct block states seen", String.valueOf(model.blockStateId("~count~")));
        row("Sample palette entries", chunks.stream().flatMap(List::stream).flatMap(s -> Arrays.stream(s.palette()))
                .distinct().sorted().limit(400).filter(k -> k.contains("[") || k.contains("{") || k.contains("grass") || k.contains("water") || k.contains("leaves")).limit(8).toList().toString());
        System.out.println();

        Map<Layouts.Key, Layouts.ShapeSection> dedupe = new HashMap<>();
        long poolBytes = 0;
        for (Layouts.ShapeSection[] row : shapeChunks) {
            for (Layouts.ShapeSection s : row) {
                if (!s.uniform() && dedupe.putIfAbsent(new Layouts.Key(s), s) == null) {
                    poolBytes += s.bytes();
                }
            }
        }
        double refsPerPlayer = 16 + 4.0 * VIEW_CHUNKS * SECTIONS_PER_CHUNK;
        System.out.println("## Estimated memory per player, view distance 10 (441 chunks)");
        System.out.println();
        System.out.println("| Layout | Per player | 100 players | 500 players |");
        System.out.println("|---|---|---|---|");
        estimate("A: block-state copy per player", perChunkA * VIEW_CHUNKS, 0);
        estimate("B: collision shapes per player", perChunkB * VIEW_CHUNKS, 0);
        estimate("C: shared pool, players spread out (worst case)", refsPerPlayer + perChunkB * VIEW_CHUNKS, 0);
        estimate("C: shared pool, players together (best case)", refsPerPlayer, poolBytes);
        System.out.printf(Locale.ROOT, "%nC pool for the whole loaded world: %s (%d unique non-uniform sections of %d)%n%n",
                mib(poolBytes), dedupe.size(), nonUniform);

        measureHeap(chunks, model);
        measureBuildTime(chunks, model);
    }

    private static int[] shapes(RegionReader.Section s, CollisionModel model) {
        int[] out = new int[s.palette().length];
        for (int i = 0; i < out.length; i++) {
            out[i] = model.shapeId(s.palette()[i]);
        }
        return out;
    }

    /** Builds real per-player structures and measures the heap they occupy. */
    private static void measureHeap(List<List<RegionReader.Section>> chunks, CollisionModel model) {
        System.out.println("## Measured heap (structures actually built; JVM -Xmx2g, G1)");
        System.out.println();
        System.out.println("| Layout | Players built | Heap used | Per player |");
        System.out.println("|---|---|---|---|");
        int n = chunks.size();
        heapRow("A: block-state copy", 10, players -> {
            List<Object> all = new ArrayList<>();
            for (int p = 0; p < players; p++) {
                Layouts.FullSection[][] view = new Layouts.FullSection[VIEW_CHUNKS][];
                for (int k = 0; k < VIEW_CHUNKS; k++) {
                    List<RegionReader.Section> c = chunks.get((p * 37 + k) % n);
                    view[k] = c.stream().map(s -> Layouts.full(s, model)).toArray(Layouts.FullSection[]::new);
                }
                all.add(view);
            }
            return all;
        });
        heapRow("B: collision shapes", 100, players -> {
            Map<Integer, Layouts.ShapeSection> pool = Layouts.newPool();
            List<Object> all = new ArrayList<>();
            for (int p = 0; p < players; p++) {
                Layouts.ShapeSection[][] view = new Layouts.ShapeSection[VIEW_CHUNKS][];
                for (int k = 0; k < VIEW_CHUNKS; k++) {
                    List<RegionReader.Section> c = chunks.get((p * 37 + k) % n);
                    view[k] = c.stream().map(s -> Layouts.shape(s.indices(), shapes(s, model), pool))
                            .toArray(Layouts.ShapeSection[]::new);
                }
                all.add(view);
            }
            return all;
        });
        heapRow("C: shared pool, players together", 100, players -> {
            Map<Integer, Layouts.ShapeSection> pool = Layouts.newPool();
            Map<Layouts.Key, Layouts.ShapeSection> shared = new HashMap<>();
            List<Object> all = new ArrayList<>();
            all.add(shared);
            for (int p = 0; p < players; p++) {
                Layouts.ShapeSection[][] view = new Layouts.ShapeSection[VIEW_CHUNKS][];
                for (int k = 0; k < VIEW_CHUNKS; k++) {
                    List<RegionReader.Section> c = chunks.get((p * 37 + k) % n);
                    view[k] = c.stream().map(s -> {
                        Layouts.ShapeSection built = Layouts.shape(s.indices(), shapes(s, model), pool);
                        return built.uniform() ? built : shared.computeIfAbsent(new Layouts.Key(built), key -> built);
                    }).toArray(Layouts.ShapeSection[]::new);
                }
                all.add(view);
            }
            return all;
        });
        System.out.println();
    }

    private static void heapRow(String name, int players, java.util.function.IntFunction<Object> builder) {
        long before = usedAfterGc();
        Object kept = builder.apply(players);
        long after = usedAfterGc();
        long used = Math.max(0, after - before);
        row(name, players + " | " + mib(used) + " | " + mib(used / players));
        if (kept.hashCode() == 42) {
            System.out.print("");
        }
    }

    private static long usedAfterGc() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return rt.totalMemory() - rt.freeMemory();
    }

    /** CPU cost of turning a received section into layout B (what chunk ingestion would do). */
    private static void measureBuildTime(List<List<RegionReader.Section>> chunks, CollisionModel model) {
        List<RegionReader.Section> all = new ArrayList<>();
        chunks.forEach(all::addAll);
        List<int[]> shapeMaps = all.stream().map(s -> shapes(s, model)).toList();
        long[] rounds = new long[10];
        for (int r = -5; r < rounds.length; r++) {
            Map<Integer, Layouts.ShapeSection> pool = Layouts.newPool();
            long start = System.nanoTime();
            long sink = 0;
            for (int i = 0; i < all.size(); i++) {
                sink += Layouts.shape(all.get(i).indices(), shapeMaps.get(i), pool).palette().length;
            }
            long elapsed = System.nanoTime() - start;
            if (r >= 0) {
                rounds[r] = elapsed / all.size();
            }
            if (sink == -1) {
                System.out.print("");
            }
        }
        Arrays.sort(rounds);
        long median = rounds[rounds.length / 2];
        System.out.println("## Build cost (layout B, after JIT warm-up)");
        System.out.println();
        System.out.println("| Metric | Value |");
        System.out.println("|---|---|");
        row("Median per section", median + " ns");
        row("Per chunk (24 sections)", fmt(median * 24 / 1000.0) + " us");
        row("Full view on join (441 chunks)", fmt(median * 24 * 441 / 1_000_000.0) + " ms (spread over chunk sends)");
    }

    private static void estimate(String name, double perPlayer, long shared) {
        row(name, mib((long) perPlayer) + " | " + mib((long) (perPlayer * 100 + shared)) + " | "
                + mib((long) (perPlayer * 500 + shared)));
    }

    private static void row(String a, String b) {
        System.out.println("| " + a + " | " + b + " |");
    }

    private static String pct(long part, long whole) {
        return String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String mib(long bytes) {
        return String.format(Locale.ROOT, "%.2f MiB", bytes / 1048576.0);
    }
}
