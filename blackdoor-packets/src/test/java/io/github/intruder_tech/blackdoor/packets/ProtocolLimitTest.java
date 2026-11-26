package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketVerdict;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket.Undecodable;
import io.github.intruder_tech.blackdoor.state.SessionRegistry;
import io.github.intruder_tech.blackdoor.violations.Finding;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Packets over the protocol's own limits, and what flood mitigation must never drop. */
class ProtocolLimitTest {

    private final ManualTimeSource time = new ManualTimeSource();
    private final List<Finding> findings = new CopyOnWriteArrayList<>();
    private final ConnectionInfo conn = new ConnectionInfo(new Object(), UUID.randomUUID(), "Steve",
            PacketSecurityEngineTest.V);

    private PacketSecurityEngine engine(CheckMode mode) {
        PacketSettings d = PacketSettings.defaults();
        Map<PacketCheck, CheckMode> modes = new EnumMap<>(PacketCheck.class);
        for (PacketCheck c : PacketCheck.values()) {
            modes.put(c, mode);
        }
        PacketSettings settings = new PacketSettings(modes, d.maxMovementBytes(), d.maxChatLength(),
                d.maxCommandLength(), d.maxTabCompleteLength(), d.movementRate(), d.interactRate(), d.textRate(),
                d.totalRate(), d.maxSessions(), d.exploits());
        return new PacketSecurityEngine(new SessionRegistry(10, time), findings::add, time, () -> settings,
                () -> true, PacketSecurityEngineTest.V,
                new RateLimitedLogger(PacketSecurityEngineTest.SILENT, time, 5, 1, 8));
    }

    private PacketVerdict in(PacketSecurityEngine engine, ServerboundPacket packet) {
        return engine.onInbound(conn, ConnectionState.PLAY, 400, packet);
    }

    private void exhaustTotalRate(PacketSecurityEngine engine) {
        for (int i = 0; i < 1_100; i++) {
            in(engine, new ServerboundPacket.Other("filler"));
        }
        assertThat(in(engine, new ServerboundPacket.Other("filler"))).isEqualTo(PacketVerdict.DROP);
    }

    @Test
    void undecodablePacketsAreReportedAgainstTheirCheck() {
        PacketSecurityEngine engine = engine(CheckMode.MONITOR);
        for (Undecodable.Kind kind : Undecodable.Kind.values()) {
            assertThat(in(engine, new Undecodable(kind))).isEqualTo(PacketVerdict.PASS);
        }
        assertThat(findings).extracting(Finding::checkId).containsExactly("packet.sign", "packet.book",
                "packet.payload", "packet.oversized", "packet.oversized", "packet.oversized", "packet.creative-item");
    }

    @Test
    void undecodablePacketsAreDroppedWhenTheCheckBlocks() {
        assertThat(in(engine(CheckMode.MITIGATE), new Undecodable(Undecodable.Kind.SIGN)))
                .isEqualTo(PacketVerdict.DROP);
    }

    @Test
    void floodMitigationNeverDropsRepliesTheServerWaitsFor() {
        PacketSecurityEngine engine = engine(CheckMode.MITIGATE);
        exhaustTotalRate(engine);

        assertThat(in(engine, new ServerboundPacket.Reply("keep_alive"))).isEqualTo(PacketVerdict.PASS);
    }

    @Test
    void floodMitigationNeverDropsTeleportConfirms() {
        PacketSecurityEngine engine = engine(CheckMode.MITIGATE);
        exhaustTotalRate(engine);

        // No login was observed, so the unknown id is not evidence of anything either.
        assertThat(in(engine, new ServerboundPacket.TeleportConfirm(7))).isEqualTo(PacketVerdict.PASS);
    }
}
