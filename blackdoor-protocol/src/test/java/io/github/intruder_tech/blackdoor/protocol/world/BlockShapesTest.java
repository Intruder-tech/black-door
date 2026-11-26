package io.github.intruder_tech.blackdoor.protocol.world;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockShapesTest {

    @Test
    void unsetUnknownAndEmptyAreDifferent() {
        BlockShapes shapes = new BlockShapes();
        shapes.put(0, List.of());
        shapes.putUnknown(2);
        assertThat(shapes.boxes(0)).contains(List.of());
        for (int id : new int[]{1, 2, -1, BlockShapes.CAPACITY}) {
            assertThat(shapes.boxes(id)).isEmpty();
        }
        assertThat(shapes.known()).isEqualTo(1);
    }

    @Test
    void specialBlocksKeepTheirBoxesAndTheMarkThroughTheFile() throws Exception {
        BlockShapes shapes = new BlockShapes();
        shapes.putSpecial(89, List.of());
        shapes.put(1, List.of());
        assertThat(shapes.boxes(89)).contains(List.of());
        assertThat(shapes.special(89)).isTrue();
        assertThat(shapes.special(1)).isFalse();
        assertThat(shapes.special(2)).isFalse();
        StringWriter out = new StringWriter();
        shapes.write(out, "26.3");
        assertThat(out.toString()).contains("89 !-\n");
        BlockShapes back = BlockShapes.read(new StringReader(out.toString()));
        assertThat(back.special(89)).isTrue();
        assertThat(back.boxes(89)).contains(List.of());
    }

    @Test
    void roundTripsThroughTheFileFormat() throws Exception {
        BlockShapes shapes = new BlockShapes();
        var slab = new BlockShapes.Box(0, 0, 0, 1, 0.5, 1);
        var post = new BlockShapes.Box(0.375, 0, 0.375, 0.625, 1.5, 0.625);
        shapes.put(0, List.of());
        shapes.put(5, List.of(slab, post));
        shapes.putUnknown(9);
        StringWriter out = new StringWriter();
        shapes.write(out, "26.3");
        assertThat(out.toString()).startsWith("server 26.3\n").contains("0 -\n").contains("9 ?\n");
        BlockShapes back = BlockShapes.read(new StringReader(out.toString()));
        assertThat(back.boxes(5)).contains(List.of(slab, post));
        assertThat(back.boxes(0)).contains(List.of());
        assertThat(back.boxes(9)).isEmpty();
    }
}
