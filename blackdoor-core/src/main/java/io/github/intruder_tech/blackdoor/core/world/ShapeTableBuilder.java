package io.github.intruder_tech.blackdoor.core.world;

import io.github.intruder_tech.blackdoor.protocol.world.BlockShapes;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Fills a {@link BlockShapes} table id by id, a batch per call so the server thread is never held
 * long. Blocks whose shape depends on where they are or who stands on them stay unknown.
 */
public final class ShapeTableBuilder {

    /** This many ids in a row without a name mean there are no more states. */
    private static final int END_AFTER = 64;
    private static final Set<String> POSITIONAL = Set.of("scaffolding", "powder_snow", "bamboo", "pointed_dripstone",
            "moving_piston");
    private static final Set<String> SPECIAL = Set.of("water", "lava", "bubble_column", "ladder", "vine",
            "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant", "cave_vines",
            "cave_vines_plant", "cobweb", "slime_block", "honey_block", "sweet_berry_bush", "kelp", "kelp_plant",
            "seagrass", "tall_seagrass");

    private final IntFunction<Optional<String>> names;
    private final Function<String, Optional<List<BlockShapes.Box>>> server;
    private final BlockShapes into;
    private final int perBatch;
    private int next;
    private int missing;

    public ShapeTableBuilder(IntFunction<Optional<String>> names,
                             Function<String, Optional<List<BlockShapes.Box>>> server, BlockShapes into, int perBatch) {
        this.names = names;
        this.server = server;
        this.into = into;
        this.perBatch = perBatch;
    }

    /** @return true while more remain */
    public boolean step() {
        for (int i = 0; i < perBatch && more(); i++, next++) {
            Optional<String> name = names.apply(next);
            if (name.isEmpty()) {
                missing++;
                continue;
            }
            missing = 0;
            add(next, name.get());
        }
        return more();
    }

    private boolean more() {
        return missing < END_AFTER && next < BlockShapes.CAPACITY;
    }

    private void add(int id, String name) {
        if (positional(name)) {
            into.putUnknown(id);
            return;
        }
        try {
            server.apply(name).ifPresentOrElse(boxes -> {
                if (special(name)) {
                    into.putSpecial(id, boxes);
                } else {
                    into.put(id, boxes);
                }
            }, () -> into.putUnknown(id));
        } catch (RuntimeException e) {
            into.putUnknown(id);
        }
    }

    private static boolean positional(String name) {
        String block = block(name);
        return POSITIONAL.contains(block) || block.endsWith("shulker_box");
    }

    private static boolean special(String name) {
        String block = block(name);
        return SPECIAL.contains(block) || block.endsWith("_bed") || name.contains("waterlogged=true");
    }

    /** The block id without namespace and properties. */
    private static String block(String name) {
        int end = name.indexOf('[');
        String block = end < 0 ? name : name.substring(0, end);
        return block.substring(block.indexOf(':') + 1);
    }
}
