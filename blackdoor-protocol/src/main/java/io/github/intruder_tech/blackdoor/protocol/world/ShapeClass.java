package io.github.intruder_tech.blackdoor.protocol.world;

/**
 * Coarse collision class of a block state. Physics treats PARTIAL and SPECIAL as "exact shape or
 * special physics needed": until a precise shape registry exists, movement checks near them give
 * no verdict.
 */
public enum ShapeClass {
    /** No collision (air, plants, torches...). */
    EMPTY,
    /** Full 1x1x1 cube. */
    FULL,
    /** Collides, but not as a full cube (slabs, stairs, fences...). */
    PARTIAL,
    /** Water / lava. */
    FLUID,
    /** Special movement physics (ladders, cobweb, slime, honey, ice, powder snow...). */
    SPECIAL
}
