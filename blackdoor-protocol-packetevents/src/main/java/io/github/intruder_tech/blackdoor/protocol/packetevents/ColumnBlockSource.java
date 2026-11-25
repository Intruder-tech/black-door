package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import io.github.intruder_tech.blackdoor.protocol.world.BlockSource;

/** Zero-copy view over the sections of a PacketEvents chunk column. */
record ColumnBlockSource(BaseChunk[] chunks) implements BlockSource {

    @Override
    public int sections() {
        return chunks.length;
    }

    @Override
    public boolean emptySection(int section) {
        return chunks[section] == null || chunks[section].isEmpty();
    }

    @Override
    public int blockId(int section, int x, int y, int z) {
        return chunks[section].getBlockId(x, y, z);
    }
}
