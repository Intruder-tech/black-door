package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketVerdict;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.state.SessionRegistry;
import io.github.intruder_tech.blackdoor.violations.Finding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionEngineTest {

    final ManualTimeSource time = new ManualTimeSource();
    final List<Finding> findings = new ArrayList<>();
    final List<Integer> sent = new ArrayList<>();
    final SessionRegistry sessions = new SessionRegistry(10, time);
    final ConnectionInfo conn = new ConnectionInfo(new Object(), UUID.randomUUID(), "Steve", PacketSecurityEngineTest.V);
    PacketSecurityEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PacketSecurityEngine(sessions, findings::add, time, PacketSettings::defaults, () -> false,
                PacketSecurityEngineTest.V, new RateLimitedLogger(PacketSecurityEngineTest.SILENT, time, 5, 1, 8), false);
    }

    PacketVerdict reply(int id) {
        return engine.onInbound(conn, ConnectionState.PLAY, 5, new ServerboundPacket.TransactionResponse(id, false));
    }

    void heartbeat() {
        engine.heartbeat((key, id) -> sent.add(id));
    }

    @Test
    void noTransactionsBeforeLoginIsObserved() {
        engine.onInbound(conn, ConnectionState.PLAY, 30, PacketSecurityEngineTest.pos(0, 64, 0));
        heartbeat();
        assertThat(sent).isEmpty();
    }

    @Test
    void ownRepliesAreConsumedAndMeasureLatency() {
        engine.onOutbound(conn, new ClientboundPacket.JoinGame(7));
        heartbeat();
        time.advance(Duration.ofMillis(80));
        assertThat(reply(sent.get(0))).isEqualTo(PacketVerdict.DROP);
        assertThat(findings).isEmpty();
        assertThat(sessions.find("Steve").orElseThrow().rttMillis()).isEqualTo(80.0);
        assertThat(engine.stats().transactionsAcknowledged()).isEqualTo(1);
        assertThat(engine.stats().dropped()).as("consumption is not mitigation").isZero();
    }

    @Test
    void otherPluginsPingsPassUntouched() {
        engine.onOutbound(conn, new ClientboundPacket.JoinGame(7));
        heartbeat();
        assertThat(reply(42)).isEqualTo(PacketVerdict.PASS);
        assertThat(findings).isEmpty();
    }

    @Test
    void forgedAndReorderedRepliesAreFlagged() {
        engine.onOutbound(conn, new ClientboundPacket.JoinGame(7));
        heartbeat();
        heartbeat();
        assertThat(reply(sent.get(1))).isEqualTo(PacketVerdict.DROP);
        assertThat(reply(Integer.MIN_VALUE + 5000)).isEqualTo(PacketVerdict.DROP);
        assertThat(findings).extracting(Finding::checkId).containsExactly("packet.transaction", "packet.transaction");
    }

    @Test
    void velocityIsAppliedOnlyAfterTheFollowingTransactionIsAcknowledged() {
        engine.onOutbound(conn, new ClientboundPacket.JoinGame(7));
        heartbeat();
        engine.onOutbound(conn, new ClientboundPacket.Velocity(7, 0.3, 0.4, 0));
        engine.onOutbound(conn, new ClientboundPacket.Velocity(99, 1, 1, 1));
        assertThat(sessions.find("Steve").orElseThrow().pendingVelocities()).isEqualTo(1);
        reply(sent.get(0));
        assertThat(sessions.find("Steve").orElseThrow().pendingVelocities()).as("ack of the earlier ping").isEqualTo(1);
        heartbeat();
        reply(sent.get(1));
        assertThat(sessions.find("Steve").orElseThrow().pendingVelocities()).isZero();
    }

    @Test
    void entityTrackingFollowsOutboundPackets() {
        engine.onOutbound(conn, new ClientboundPacket.JoinGame(7));
        engine.onOutbound(conn, new ClientboundPacket.EntitySpawn(10, 0, 64, 0));
        engine.onOutbound(conn, new ClientboundPacket.EntitySpawn(11, 5, 64, 5));
        engine.onOutbound(conn, new ClientboundPacket.EntityMove(10, 1, 0, 0));
        engine.onOutbound(conn, new ClientboundPacket.EntityRemove(new int[]{11}));
        assertThat(sessions.find("Steve").orElseThrow().trackedEntities()).isEqualTo(1);
    }
}
