package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeClass;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeRegistry;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Coarse collision classes from PacketEvents block data (air / blocks-motion flags plus names for
 * fluids, special-physics and non-cube blocks). Results are cached per global id. Unknown ids are
 * SPECIAL so physics gives no verdict rather than guessing.
 */
final class PacketEventsShapes implements ShapeRegistry {

    private static final int MAX_ID = 1 << 17;
    private static final List<String> SPECIAL = List.of("ladder", "vine", "scaffolding", "cobweb", "honey_block",
            "slime_block", "soul_sand", "powder_snow", "ice", "magma_block", "sweet_berry_bush", "bubble_column",
            "bed", "bamboo");
    private static final List<String> PARTIAL = List.of("slab", "stairs", "fence", "wall", "pane", "door", "bars",
            "chest", "carpet", "snow", "lantern", "candle", "cake", "cactus", "anvil", "hopper", "cauldron", "lectern",
            "campfire", "path", "farmland", "chain", "rod", "head", "skull", "pot", "bell", "dragon_egg", "conduit",
            "frame", "grindstone", "stonecutter", "detector", "repeater", "comparator", "piston", "pickle", "egg",
            "amethyst", "dripstone", "lily_pad", "azalea", "table", "composter", "dripleaf", "sniffer", "shelf",
            "trapdoor", "heavy_core", "vault", "spawner");

    private final AtomicReferenceArray<ShapeClass> cache = new AtomicReferenceArray<>(MAX_ID);

    @Override
    public ShapeClass classify(int id) {
        if (id < 0 || id >= MAX_ID) {
            return ShapeClass.SPECIAL;
        }
        ShapeClass known = cache.get(id);
        if (known != null) {
            return known;
        }
        ShapeClass computed = compute(id);
        cache.set(id, computed);
        return computed;
    }

    private static ShapeClass compute(int id) {
        try {
            StateType type = WrappedBlockState.getByGlobalId(id).getType();
            if (type.isAir()) {
                return ShapeClass.EMPTY;
            }
            String name = type.getName().toLowerCase(Locale.ROOT);
            if (name.equals("water") || name.equals("lava")) {
                return ShapeClass.FLUID;
            }
            for (String s : SPECIAL) {
                if (name.contains(s)) {
                    return ShapeClass.SPECIAL;
                }
            }
            if (!type.isBlocking()) {
                return ShapeClass.EMPTY;
            }
            for (String p : PARTIAL) {
                if (name.contains(p)) {
                    return ShapeClass.PARTIAL;
                }
            }
            return ShapeClass.FULL;
        } catch (RuntimeException | LinkageError e) {
            return ShapeClass.SPECIAL;
        }
    }
}
