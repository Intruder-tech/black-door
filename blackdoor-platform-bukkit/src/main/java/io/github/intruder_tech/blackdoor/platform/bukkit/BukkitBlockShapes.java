package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.protocol.world.BlockShapes;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Collision boxes as the running server defines them. By reflection, because this module compiles
 * against the 1.8.8 API and older servers have no such methods.
 */
final class BukkitBlockShapes implements Function<String, Optional<List<BlockShapes.Box>>> {

    private final Method createBlockData;
    private final Method collisionShape;
    private final Method boundingBoxes;
    private final Method[] corners;

    private BukkitBlockShapes() throws ReflectiveOperationException {
        createBlockData = Bukkit.class.getMethod("createBlockData", String.class);
        collisionShape = Class.forName("org.bukkit.block.data.BlockData").getMethod("getCollisionShape", Location.class);
        boundingBoxes = Class.forName("org.bukkit.util.VoxelShape").getMethod("getBoundingBoxes");
        Class<?> box = Class.forName("org.bukkit.util.BoundingBox");
        corners = new Method[]{box.getMethod("getMinX"), box.getMethod("getMinY"), box.getMethod("getMinZ"),
                box.getMethod("getMaxX"), box.getMethod("getMaxY"), box.getMethod("getMaxZ")};
    }

    /** Null when this server has no way to tell collision shapes. */
    static Function<String, Optional<List<BlockShapes.Box>>> create() {
        try {
            return new BukkitBlockShapes();
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    @Override
    public Optional<List<BlockShapes.Box>> apply(String state) {
        if (Bukkit.getWorlds().isEmpty()) {
            return Optional.empty();
        }
        try {
            Object data = createBlockData.invoke(null, state);
            Object shape = collisionShape.invoke(data, new Location(Bukkit.getWorlds().get(0), 0, 0, 0));
            List<BlockShapes.Box> boxes = new ArrayList<>();
            for (Object box : (Collection<?>) boundingBoxes.invoke(shape)) {
                double[] v = new double[corners.length];
                for (int i = 0; i < corners.length; i++) {
                    v[i] = (Double) corners[i].invoke(box);
                }
                boxes.add(new BlockShapes.Box(v[0], v[1], v[2], v[3], v[4], v[5]));
            }
            return Optional.of(boxes);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
