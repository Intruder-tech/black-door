package io.github.intruder_tech.blackdoor.protocol.world;

/**
 * Read-only view over the block sections of one chunk packet. Valid only during the packet
 * callback; consumers must copy what they need (never keep the view).
 */
public interface BlockSource {

    int sections();

    /** True if the section is all air (or absent). */
    boolean emptySection(int section);

    /** Global block-state id at local coordinates (0..15 each). */
    int blockId(int section, int x, int y, int z);
}
