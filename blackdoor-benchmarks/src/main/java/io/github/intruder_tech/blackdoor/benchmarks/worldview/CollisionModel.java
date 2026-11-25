package io.github.intruder_tech.blackdoor.benchmarks.worldview;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spike-only mapping from block states to collision-shape ids, approximated by block name.
 * Good enough to measure memory (what matters is how many distinct shapes a section holds);
 * the real registry (milestone 2.4) will be generated from the game's own shape data.
 */
final class CollisionModel {

    static final int EMPTY = 0;
    static final int FULL = 1;

    private static final List<String> EMPTY_PARTS = List.of("air", "sapling", "torch", "button", "pressure_plate",
            "rail", "redstone_wire", "sign", "banner", "fire", "flower", "tulip", "orchid", "allium", "bluet", "daisy",
            "cornflower", "lily_of_the_valley", "dandelion", "poppy", "fern", "dead_bush", "kelp", "seagrass",
            "sugar_cane", "roots", "sprouts", "lever", "tripwire", "light", "structure_void", "short_grass",
            "tall_grass", "glow_lichen", "moss_carpet", "pink_petals", "leaf_litter", "firefly_bush", "bush");
    private static final List<String> SPECIAL = List.of("ladder", "vine", "scaffolding", "cobweb", "honey_block",
            "slime_block", "soul_sand", "powder_snow", "ice", "magma_block", "sweet_berry_bush", "bubble_column");
    private static final List<String> PARTIAL_PARTS = List.of("slab", "stairs", "fence", "wall", "pane", "door",
            "bars", "bed", "chest", "carpet", "snow", "lantern", "candle", "cake", "cactus", "anvil", "hopper",
            "cauldron", "lectern", "campfire", "path", "farmland", "chain", "rod", "head", "skull", "pot", "bell",
            "dragon_egg", "conduit", "frame", "grindstone", "stonecutter", "detector", "repeater", "comparator",
            "piston_head", "pickle", "egg", "amethyst_cluster", "bud", "dripstone", "lily_pad", "azalea", "table",
            "composter", "bamboo", "big_dripleaf", "sniffer");

    private final Map<String, Integer> shapeIds = new HashMap<>();
    private final Map<String, Integer> blockStateIds = new HashMap<>();

    CollisionModel() {
        shapeIds.put("empty", EMPTY);
        shapeIds.put("full", FULL);
    }

    /** Global block-state id (stand-in for the protocol's global palette id). */
    int blockStateId(String paletteKey) {
        return blockStateIds.computeIfAbsent(paletteKey, k -> blockStateIds.size());
    }

    int shapeId(String paletteKey) {
        return shapeIds.computeIfAbsent(shapeKey(paletteKey), k -> shapeIds.size());
    }

    int distinctShapes() {
        return shapeIds.size();
    }

    static String shapeKey(String paletteKey) {
        String name = paletteKey.contains("{") ? paletteKey.substring(0, paletteKey.indexOf('{')) : paletteKey;
        String simple = name.startsWith("minecraft:") ? name.substring(10) : name;
        if (simple.equals("water") || simple.equals("lava")) {
            return simple;
        }
        for (String s : SPECIAL) {
            if (simple.contains(s)) {
                return simple;
            }
        }
        if (simple.equals("grass_block") || simple.equals("snow_block")) {
            return "full";
        }
        for (String p : PARTIAL_PARTS) {
            if (simple.contains(p)) {
                return paletteKey.replace(", waterlogged=true", "").replace(", waterlogged=false", "")
                        .replace("waterlogged=true", "").replace("waterlogged=false", "");
            }
        }
        for (String e : EMPTY_PARTS) {
            if (simple.contains(e)) {
                return "empty";
            }
        }
        return "full";
    }
}
