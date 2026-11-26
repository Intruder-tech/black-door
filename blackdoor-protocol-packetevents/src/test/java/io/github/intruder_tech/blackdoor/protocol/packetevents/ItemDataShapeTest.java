package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import io.github.intruder_tech.blackdoor.protocol.packet.StructureSize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemDataShapeTest {

    @Test
    void measuresNestedTags() {
        NBTCompound inner = new NBTCompound();
        inner.setTag("value", new NBTInt(1));
        NBTCompound root = new NBTCompound();
        root.setTag("inner", inner);

        StructureSize size = StructureSize.of((Object) root, PacketTranslator::children);

        assertThat(size.depth()).isEqualTo(3);
        assertThat(size.elements()).isEqualTo(3);
    }
}
