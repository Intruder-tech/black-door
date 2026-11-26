package io.github.intruder_tech.blackdoor.physics.replay;

import io.github.intruder_tech.blackdoor.protocol.world.BlockShapes;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class VerticalReplayTest {

    private static final BlockShapes.Box FULL = new BlockShapes.Box(0, 0, 0, 1, 1, 1);

    /** Ids: 0 air, 1 stone, 2 water, 3 fence, 4 bottom slab. */
    private static BlockShapes shapes() {
        BlockShapes shapes = new BlockShapes();
        shapes.put(0, List.of());
        shapes.put(1, List.of(FULL));
        shapes.putSpecial(2, List.of());
        shapes.put(3, List.of(new BlockShapes.Box(0.375, 0, 0.375, 0.625, 1.5, 0.625)));
        shapes.put(4, List.of(new BlockShapes.Box(0, 0, 0, 1, 0.5, 1)));
        return shapes;
    }

    private static TraceWorld world(int... xyzId) {
        TraceWorld world = new TraceWorld();
        world.apply(Map.of("k", "chunk", "x", 0.0, "z", 0.0, "minSection", 0.0, "sections", List.of()));
        for (int i = 0; i < xyzId.length; i += 4) {
            world.apply(Map.of("k", "block", "x", (double) xyzId[i], "y", (double) xyzId[i + 1],
                    "z", (double) xyzId[i + 2], "id", (double) xyzId[i + 3]));
        }
        return world;
    }

    private static double dy(TraceWorld world, double x, double y, double attempt) {
        return world.clip(shapes(), x, y, 5.5, 1.8, attempt).orElseThrow().dy();
    }

    @Test
    void clipsAgainstFloorsCeilingsAndTallBoxes() {
        Offset<Double> close = Offset.offset(1e-9);
        assertThat(dy(world(5, 10, 5, 1), 5.5, 11.2, -0.5)).as("floor").isCloseTo(-0.2, close);
        assertThat(dy(world(5, 13, 5, 1), 5.5, 11.0, 0.42)).as("ceiling").isCloseTo(0.2, close);
        assertThat(dy(world(5, 10, 5, 3), 5.5, 11.8, -0.5)).as("fence").isCloseTo(-0.3, close);
        assertThat(dy(world(5, 10, 5, 1, 6, 11, 5, 1), 5.9, 12.1, -0.5)).as("straddling").isCloseTo(-0.1, close);
        assertThat(world(5, 11, 5, 2).clip(shapes(), 5.5, 11.2, 5.5, 1.8, -0.1)).as("water").isEmpty();
        assertThat(world().clip(shapes(), 100.5, 11, 5.5, 1.8, -0.1)).as("unknown chunk").isEmpty();
    }

    private static String move(double y, boolean ground) {
        return "{\"k\":\"move\",\"x\":5.5,\"y\":" + y + ",\"z\":5.5,\"pos\":true,\"ground\":" + ground
                + "}\n{\"k\":\"tick\"}";
    }

    /** A stone block at (5, 10, 5), so its top is at y = 11, then the given lines. */
    private static VerticalReplay.Result replay(String... lines) throws IOException {
        List<String> all = new ArrayList<>(List.of("{\"k\":\"meta\",\"v\":2}",
                "{\"k\":\"chunk\",\"x\":0,\"z\":0,\"minSection\":0,\"sections\":[]}",
                "{\"k\":\"block\",\"x\":5,\"y\":10,\"z\":5,\"id\":1}"));
        all.addAll(List.of(lines));
        return VerticalReplay.run(Trace.read(new StringReader(String.join("\n", all))), shapes());
    }

    @Test
    void aFallAndAJumpMatch() throws IOException {
        double y = 20;
        double v = 0;
        List<String> fall = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            fall.add(move(y, false));
            v = (v - 0.08) * 0.98;
            y += v;
        }
        fall.add(1, event("k=velocity", "y=-0.0784"));
        var fallen = replay(fall.toArray(String[]::new));
        assertThat(fallen.misses()).isZero();
        assertThat(fallen.matches()).isEqualTo(5);

        var jump = replay(move(11, true), move(11, true), "{\"k\":\"input\",\"jump\":true}", move(11.42, false),
                move(11.42 + 0.3332, false));
        assertThat(jump.misses()).isZero();
        assertThat(jump.matches()).isEqualTo(3);

        var boosted = replay("{\"k\":\"effect\",\"name\":\"minecraft:jump_boost\",\"amplifier\":1}", move(11, true),
                move(11, true), "{\"k\":\"input\",\"jump\":true}", move(11.62, false));
        assertThat(boosted.misses()).isZero();
        assertThat(boosted.matches()).isEqualTo(2);
    }

    @Test
    void hoveringIsAMissButNotInCreative() throws IOException {
        var hover = replay(move(20, false), move(20, false), move(20, false), move(20, false));
        assertThat(hover.misses()).isEqualTo(2);
        var creative = replay("{\"k\":\"gamemode\",\"creative\":true}", move(20, false), move(20, false),
                move(20, false), move(20, false));
        assertThat(creative.misses()).isZero();
        assertThat(creative.noVerdict()).containsEntry("creative", 3);
    }

    @Test
    void theTickAfterWaterGivesNoVerdict() throws IOException {
        var result = replay("{\"k\":\"block\",\"x\":5,\"y\":20,\"z\":5,\"id\":2}", move(20, false), move(20.1, false),
                move(22.5, false), move(22.767, false), move(22.95026, false));
        assertThat(result.misses()).isZero();
        assertThat(result.noVerdict()).containsKey("block");
    }

    @Test
    void steppingOntoAHigherBlockWhileMovingIsPredicted() throws IOException {
        String step = "{\"k\":\"move\",\"x\":6.5,\"y\":11.5,\"z\":5.5,\"pos\":true,\"ground\":true}\n{\"k\":\"tick\"}";
        var result = replay("{\"k\":\"block\",\"x\":6,\"y\":10,\"z\":5,\"id\":1}",
                "{\"k\":\"block\",\"x\":6,\"y\":11,\"z\":5,\"id\":4}", move(11, true), move(11, true), step);
        assertThat(result.misses()).isZero();
        assertThat(result.matches()).isEqualTo(2);
    }

    /** One event line from "key=value" pairs; numbers, booleans and [] are written bare. */
    private static String event(String... pairs) {
        StringBuilder line = new StringBuilder("{");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            boolean bare = kv[1].matches("-?[0-9.]+|true|false|\\[\\]");
            line.append(line.length() > 1 ? "," : "").append('"').append(kv[0]).append("\":")
                    .append(bare ? kv[1] : '"' + kv[1] + '"');
        }
        return line.append('}').toString();
    }

    @Test
    void claimingToBeOnTheGroundInTheAirIsAMiss() throws IOException {
        var result = replay(move(11, true), move(11, true), event("k=input", "jump=true"), move(11.42, true),
                move(11.84, true));
        assertThat(result.misses()).isEqualTo(2);
    }

    @Test
    void aFlyingPacketCountsOnlyWhenTheServerAllowsFlight() throws IOException {
        String flying = event("k=flying", "flying=true");
        var cheat = replay(flying, move(20, false), move(20, false), move(20, false), move(20, false));
        assertThat(cheat.misses()).isEqualTo(2);
        var allowed = replay(event("k=abilities", "flying=false", "allowed=true"), flying, move(20, false),
                move(20, false), move(20, false), move(20, false));
        assertThat(allowed.misses()).isZero();
        assertThat(allowed.noVerdict()).containsEntry("flying", 3);
    }

    @Test
    void knockbackIsPredictedNotSkipped() throws IOException {
        var result = replay(move(11, true), move(11, true), event("k=velocity", "y=0.3"), move(11.3, false),
                move(11.5156, false));
        assertThat(result.misses()).isZero();
        assertThat(result.matches()).isEqualTo(3);
        var hover = replay(move(11, true), move(11, true), event("k=velocity", "y=0.3"), move(14, false),
                move(14, false));
        assertThat(hover.misses()).isPositive();
    }

    @Test
    void aRiseNoJumpCouldGiveIsAMissEvenWhenTheSpeedIsUnknown() throws IOException {
        assertThat(replay(move(20, false), move(21.5, false)).misses()).isEqualTo(1);
    }

    @Test
    void changedJumpAttributesGiveNoVerdict() throws IOException {
        var result = replay(event("k=attribute", "name=minecraft:jump_strength", "base=0.8", "modifiers=[]"),
                move(11, true), move(11, true), move(11.8, false));
        assertThat(result.misses()).isZero();
        assertThat(result.noVerdict()).containsKey("attribute");
    }

    @Test
    void aBodyThatDoesNotFitStandingGivesNoVerdict() throws IOException {
        var result = replay(event("k=block", "x=5", "y=12", "z=5", "id=1"), move(11, true), move(11, true),
                move(11.2, false));
        assertThat(result.misses()).isZero();
        assertThat(result.noVerdict()).containsKey("pose");
    }

    private static Path testData(String path) {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.exists(here.resolve("testdata"))) {
            here = here.getParent();
        }
        return Objects.requireNonNull(here, "testdata not found").resolve("testdata").resolve(path);
    }

    private static Reader gzip(Path file) throws IOException {
        return new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8);
    }

    @Test
    void goldenTracesHaveNoMisses() throws IOException {
        BlockShapes shapes;
        try (Reader in = gzip(testData("block-shapes/26.3.txt.gz"))) {
            shapes = BlockShapes.read(in);
        }
        List<String> failures = new ArrayList<>();
        for (String name : List.of("trace-101-sprint-fall-knockback", "trace-102-sprint-jump-effects",
                "trace-104-sprint-sneak-wall")) {
            VerticalReplay.Result r;
            try (Reader in = gzip(testData("golden-traces/26.3/" + name + ".jsonl.gz"))) {
                r = VerticalReplay.run(Trace.read(in), shapes);
            }
            System.out.println(name + ": " + r);
            if (r.misses() > 0) {
                failures.add(name + " misses " + r.misses() + " " + r.firstMisses() + " none " + r.noVerdict()
                        + " matches " + r.matches());
            }
            if (name.contains("102") && r.airborneJudged() < 0.8 * r.airborne()) {
                failures.add(name + " judged only " + r.airborneJudged() + " of " + r.airborne() + " " + r.noVerdict());
            }
        }
        assertThat(failures).isEmpty();
    }
}
