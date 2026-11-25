package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketVerdict;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket.Movement;
import io.github.intruder_tech.blackdoor.state.SessionRegistry;
import io.github.intruder_tech.blackdoor.violations.Finding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PacketSecurityEngineTest {

    static final MinecraftVersion V = MinecraftVersion.of(1, 21, 4);
    static final BdLogger SILENT = new BdLogger() {
        @Override
        public void debug(String m) {
        }

        @Override
        public void info(String m) {
        }

        @Override
        public void warn(String m) {
        }

        @Override
        public void error(String m, Throwable e) {
        }
    };

    final ManualTimeSource time = new ManualTimeSource();
    final List<Finding> findings = new CopyOnWriteArrayList<>();
    final AtomicReference<PacketSettings> settings = new AtomicReference<>(PacketSettings.defaults());
    final AtomicBoolean enforce = new AtomicBoolean(false);
    final ConnectionInfo conn = new ConnectionInfo(new Object(), UUID.randomUUID(), "Steve", V);
    PacketSecurityEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PacketSecurityEngine(new SessionRegistry(100, time), findings::add, time, settings::get,
                enforce::get, V, new RateLimitedLogger(SILENT, time, 5, 1, 8));
    }

    PacketVerdict in(ServerboundPacket packet) {
        return engine.onInbound(conn, ConnectionState.PLAY, 30, packet);
    }

    static Movement pos(double x, double y, double z) {
        return new Movement(x, y, z, 0f, 0f, true, false, true);
    }

    static Movement rot(float yaw, float pitch) {
        return new Movement(0, 0, 0, yaw, pitch, false, true, true);
    }

    List<String> ids() {
        return findings.stream().map(Finding::checkId).toList();
    }

    private void setMode(PacketCheck check, CheckMode mode) {
        PacketSettings d = settings.get();
        Map<PacketCheck, CheckMode> modes = new EnumMap<>(d.modes());
        modes.put(check, mode);
        settings.set(new PacketSettings(modes, d.maxMovementBytes(), d.maxChatLength(), d.maxCommandLength(),
                d.maxTabCompleteLength(), d.movementRate(), d.interactRate(), d.textRate(), d.totalRate(), d.maxSessions()));
    }

    @Test
    void legitimatePacketsProduceNoFindings() {
        in(pos(100.5, 64, -20.25));
        in(rot(720f, 90f));
        in(rot(-3600f, -90f));
        in(new ServerboundPacket.HeldItemChange(8));
        in(new ServerboundPacket.Text(ServerboundPacket.Text.Kind.CHAT, 256));
        in(pos(29_999_984, 320, -29_999_984));
        assertThat(findings).isEmpty();
    }

    @Test
    void flagsNonFiniteAndOutOfWorldPositions() {
        in(pos(Double.NaN, 64, 0));
        in(pos(0, Double.POSITIVE_INFINITY, 0));
        in(pos(3.0E7 + 1, 64, 0));
        in(pos(0, -2.1E7, 0));
        assertThat(ids()).containsOnly("packet.invalid-position").hasSize(4);
    }

    @Test
    void flagsImpossiblePitchAndNonFiniteRotation() {
        in(rot(0f, 90.5f));
        in(rot(0f, -180f));
        in(rot(Float.NaN, 0f));
        assertThat(ids()).containsOnly("packet.invalid-rotation").hasSize(3);
    }

    @Test
    void flagsInvalidSlot() {
        in(new ServerboundPacket.HeldItemChange(9));
        in(new ServerboundPacket.HeldItemChange(-1));
        assertThat(ids()).containsExactly("packet.invalid-slot", "packet.invalid-slot");
    }

    @Test
    void selfInteractNeedsKnownEntityId() {
        in(new ServerboundPacket.InteractEntity(42, ServerboundPacket.InteractEntity.Action.ATTACK));
        assertThat(findings).isEmpty();
        engine.onOutbound(conn, new ClientboundPacket.JoinGame(42));
        in(new ServerboundPacket.InteractEntity(42, ServerboundPacket.InteractEntity.Action.ATTACK));
        in(new ServerboundPacket.InteractEntity(43, ServerboundPacket.InteractEntity.Action.ATTACK));
        assertThat(ids()).containsExactly("packet.self-interact");
    }

    @Test
    void teleportConfirmOnlyFlagsUnknownIdsAfterObservedLogin() {
        in(new ServerboundPacket.TeleportConfirm(7));
        assertThat(findings).as("login not observed: no verdict").isEmpty();
        engine.onOutbound(conn, new ClientboundPacket.JoinGame(1));
        engine.onOutbound(conn, new ClientboundPacket.SyncPosition(5));
        in(new ServerboundPacket.TeleportConfirm(5));
        assertThat(findings).isEmpty();
        in(new ServerboundPacket.TeleportConfirm(6));
        assertThat(ids()).containsExactly("packet.teleport-confirm");
    }

    @Test
    void teleportConfirmIsSkippedUnderProtocolTranslation() {
        ConnectionInfo legacy = new ConnectionInfo(new Object(), UUID.randomUUID(), "Old", MinecraftVersion.of(1, 8));
        engine.onOutbound(legacy, new ClientboundPacket.JoinGame(1));
        engine.onInbound(legacy, ConnectionState.PLAY, 10, new ServerboundPacket.TeleportConfirm(99));
        assertThat(findings).isEmpty();
    }

    @Test
    void oversizedTextAndMovement() {
        in(new ServerboundPacket.Text(ServerboundPacket.Text.Kind.CHAT, 257));
        engine.onInbound(conn, ConnectionState.PLAY, 500, pos(0, 64, 0));
        assertThat(ids()).containsExactly("packet.oversized", "packet.oversized");
    }

    @Test
    void floodIsReportedAtMostOncePerSecondWithExcessCount() {
        for (int i = 0; i < 260; i++) {
            in(pos(0, 64, 0));
        }
        assertThat(ids()).containsExactly("packet.flood");
        time.advance(Duration.ofSeconds(1));
        for (int i = 0; i < 100; i++) {
            in(pos(0, 64, 0));
        }
        assertThat(ids()).hasSize(2);
        assertThat(findings.get(1).detail()).contains("movement");
    }

    @Test
    void normalMovementRateNeverFloods() {
        for (int i = 0; i < 20 * 600; i++) {
            in(pos(i * 0.1, 64, 0));
            time.advance(Duration.ofMillis(50));
        }
        assertThat(findings).isEmpty();
    }

    @Test
    void monitorNeverDropsAndMitigateDropsOnlyWhenEnforcing() {
        assertThat(in(rot(0, 95f))).isEqualTo(PacketVerdict.PASS);
        setMode(PacketCheck.INVALID_ROTATION, CheckMode.MITIGATE);
        assertThat(in(rot(0, 95f))).as("global mode monitor").isEqualTo(PacketVerdict.PASS);
        enforce.set(true);
        assertThat(in(rot(0, 95f))).isEqualTo(PacketVerdict.DROP);
        assertThat(findings.get(2).mitigated()).isTrue();
        assertThat(engine.stats().dropped()).isEqualTo(1);
    }

    @Test
    void disabledCheckReportsNothing() {
        setMode(PacketCheck.INVALID_SLOT, CheckMode.DISABLED);
        in(new ServerboundPacket.HeldItemChange(42));
        assertThat(findings).isEmpty();
    }

    @Test
    void nonPlayStatesAreIgnored() {
        engine.onInbound(conn, ConnectionState.CONFIGURATION, 10, rot(0, 500f));
        assertThat(findings).isEmpty();
    }

    @Test
    void internalErrorsFailOpen() {
        PacketSecurityEngine broken = new PacketSecurityEngine(new SessionRegistry(10, time),
                f -> {
                    throw new IllegalStateException("sink down");
                }, time, settings::get, () -> true, V, new RateLimitedLogger(SILENT, time, 5, 1, 8));
        assertThat(broken.onInbound(conn, ConnectionState.PLAY, 10, rot(0, 95f))).isEqualTo(PacketVerdict.PASS);
        assertThat(broken.stats().errors()).isEqualTo(1);
    }

    @Test
    void disconnectReleasesSession() {
        in(pos(0, 64, 0));
        assertThat(engine.stats().sessions()).isEqualTo(1);
        engine.onDisconnect(conn.key());
        assertThat(engine.stats().sessions()).isZero();
    }

    /** Property: any finite, in-world position and any clamped rotation is never flagged. */
    @Test
    void randomLegitimateMovementIsNeverFlagged() {
        Random random = new Random(20261126L);
        for (int i = 0; i < 20_000; i++) {
            double x = (random.nextDouble() * 2 - 1) * 2.9999984E7;
            double y = random.nextDouble() * 4096 - 2048;
            double z = (random.nextDouble() * 2 - 1) * 2.9999984E7;
            float yaw = (random.nextFloat() * 2 - 1) * 100_000f;
            float pitch = (random.nextFloat() * 2 - 1) * 90f;
            in(new Movement(x, y, z, yaw, pitch, random.nextBoolean(), random.nextBoolean(), random.nextBoolean()));
            time.advance(Duration.ofMillis(50));
        }
        assertThat(findings).isEmpty();
    }

    @Test
    void latencyIsRecorded() {
        in(pos(0, 64, 0));
        assertThat(engine.stats().processed()).isEqualTo(1);
    }
}
