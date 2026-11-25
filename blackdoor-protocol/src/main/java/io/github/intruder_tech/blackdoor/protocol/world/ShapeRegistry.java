package io.github.intruder_tech.blackdoor.protocol.world;

/** Maps a global block-state id (as sent in chunk packets) to its collision class. Must be thread-safe. */
@FunctionalInterface
public interface ShapeRegistry {

    ShapeClass classify(int globalBlockStateId);
}
