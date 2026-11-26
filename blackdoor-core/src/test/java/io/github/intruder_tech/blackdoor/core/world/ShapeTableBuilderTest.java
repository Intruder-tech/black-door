package io.github.intruder_tech.blackdoor.core.world;

import io.github.intruder_tech.blackdoor.protocol.world.BlockShapes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShapeTableBuilderTest {

    @Test
    void marksBlocksThatMoveAPlayerDifferently() {
        List<String> states = List.of("minecraft:water[level=0]", "minecraft:oak_slab[type=bottom,waterlogged=true]",
                "minecraft:red_bed[part=foot]", "minecraft:stone", "minecraft:ladder[facing=north]");
        BlockShapes into = new BlockShapes();
        new ShapeTableBuilder(id -> id < states.size() ? Optional.of(states.get(id)) : Optional.empty(),
                name -> Optional.of(List.of()), into, 100).step();
        assertThat(into.special(0)).isTrue();
        assertThat(into.special(1)).isTrue();
        assertThat(into.special(2)).isTrue();
        assertThat(into.special(3)).isFalse();
        assertThat(into.special(4)).isTrue();
    }

    @Test
    void buildsInBatchesAndMarksWhatItCannotKnow() {
        List<String> states = List.of("minecraft:air", "minecraft:stone", "minecraft:scaffolding[bottom=false]",
                "minecraft:broken", "minecraft:red_shulker_box[facing=up]");
        var full = new BlockShapes.Box(0, 0, 0, 1, 1, 1);
        BlockShapes into = new BlockShapes();
        ShapeTableBuilder builder = new ShapeTableBuilder(
                id -> id < states.size() ? Optional.of(states.get(id)) : Optional.empty(),
                name -> {
                    if (name.contains("broken")) {
                        throw new IllegalArgumentException(name);
                    }
                    return Optional.of(name.equals("minecraft:air") ? List.of() : List.of(full));
                }, into, 2);
        int steps = 1;
        while (builder.step()) {
            steps++;
        }
        assertThat(steps).isGreaterThan(2);
        assertThat(into.boxes(0)).contains(List.of());
        assertThat(into.boxes(1)).contains(List.of(full));
        assertThat(into.boxes(2)).isEmpty();
        assertThat(into.boxes(3)).isEmpty();
        assertThat(into.boxes(4)).isEmpty();
    }
}
