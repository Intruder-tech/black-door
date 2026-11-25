package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.protocol.world.BlockSource;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeClass;
import io.github.intruder_tech.blackdoor.state.PlayerSession;
import io.github.intruder_tech.blackdoor.state.SessionRegistry;
import io.github.intruder_tech.blackdoor.world.PlayerWorldView;
import io.github.intruder_tech.blackdoor.world.WorldCache;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorldTrackingEngineTest {

    static final BlockSource STONE_FLOOR = new BlockSource() {
        @Override
        public int sections() {
            return 24;
        }

        @Override
        public boolean emptySection(int section) {
            return section != 4;
        }

        @Override
        public int blockId(int section, int x, int y, int z) {
            return y == 0 ? 1 : 0;
        }
    };

    @Test
    void chunksBlockChangesAcknowledgementAndDisconnect() {
        ManualTimeSource time = new ManualTimeSource();
        SessionRegistry sessions = new SessionRegistry(10, time);
        PacketSecurityEngine engine = new PacketSecurityEngine(sessions, f -> { }, time, PacketSettings::defaults,
                () -> false, PacketSecurityEngineTest.V, new RateLimitedLogger(PacketSecurityEngineTest.SILENT, time, 5, 1, 8), false);
        WorldCache cache = new WorldCache(1 << 24);
        engine.enableWorldTracking(cache, id -> id == 1 ? ShapeClass.FULL : ShapeClass.EMPTY);
        ConnectionInfo conn = new ConnectionInfo(new Object(), UUID.randomUUID(), "Steve", PacketSecurityEngineTest.V);

        engine.onOutbound(conn, new ClientboundPacket.JoinGame(7));
        engine.onOutbound(conn, new ClientboundPacket.WorldChange("world"));
        engine.onOutbound(conn, new ClientboundPacket.ChunkData(0, 0, -4, STONE_FLOOR));
        PlayerSession session = sessions.findSession("Steve").orElseThrow();
        PlayerWorldView view = engine.worldView(session).orElseThrow();
        assertThat(view.shapeAt(3, 0, 3)).isEqualTo(ShapeClass.FULL);
        assertThat(view.shapeAt(3, 1, 3)).isEqualTo(ShapeClass.EMPTY);
        assertThat(cache.stats().chunks()).isEqualTo(1);

        List<Integer> sent = new ArrayList<>();
        engine.heartbeat((key, id) -> sent.add(id));
        engine.onOutbound(conn, new ClientboundPacket.BlockChange(3, 0, 3, 0));
        assertThat(view.shapeAt(3, 0, 3)).isEqualTo(ShapeClass.EMPTY);
        assertThat(view.uncertainNear(3, 1, 3, 1)).isTrue();
        engine.heartbeat((key, id) -> sent.add(id));
        engine.onInbound(conn, ConnectionState.PLAY, 5, new ServerboundPacket.TransactionResponse(sent.get(0), false));
        assertThat(view.uncertainNear(3, 1, 3, 1)).as("ack of the ping sent before the change").isTrue();
        engine.onInbound(conn, ConnectionState.PLAY, 5, new ServerboundPacket.TransactionResponse(sent.get(1), false));
        assertThat(view.uncertainNear(3, 1, 3, 1)).isFalse();

        engine.onDisconnect(conn.key());
        assertThat(cache.stats().chunks()).isZero();
    }
}
