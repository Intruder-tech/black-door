package io.github.intruder_tech.blackdoor.core.trace;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.FakePlatform;
import io.github.intruder_tech.blackdoor.core.command.AwaitMessages;
import io.github.intruder_tech.blackdoor.core.command.RecordingSender;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.protocol.world.BlockSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TraceRecorderTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;
    private final ConnectionInfo conn = new ConnectionInfo(new Object(), UUID.randomUUID(), "Steve", MinecraftVersion.of(1, 21, 4));
    private final RecordingSender admin = new RecordingSender("Admin", Set.of("blackdoor.command.record"));

    @BeforeEach
    void setUp() {
        engine = new BlackDoorEngine(FakePlatform.paper(dir), TimeSource.system());
        engine.enable();
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    private PacketHandler packets() {
        return engine.packetEngine().orElseThrow();
    }

    private void command(String... args) {
        engine.commands().dispatch(admin, args);
    }

    private void in(ServerboundPacket packet) {
        packets().onInbound(conn, ConnectionState.PLAY, 30, packet);
    }

    private void out(ClientboundPacket packet) {
        packets().onOutbound(conn, packet);
    }

    private List<String> savedTrace() throws Exception {
        AwaitMessages.await(admin, m -> m.contains("saved"));
        List<Path> files;
        try (var list = Files.list(dir.resolve("traces"))) {
            files = list.toList();
        }
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).endsWith(".jsonl.gz");
        try (var reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(files.get(0))), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** One section with stone on the bottom layer and air above, then an empty section. */
    private static BlockSource floor() {
        return new BlockSource() {
            @Override
            public int sections() {
                return 2;
            }

            @Override
            public boolean emptySection(int section) {
                return section == 1;
            }

            @Override
            public int blockId(int section, int x, int y, int z) {
                return y == 0 ? 1 : 0;
            }
        };
    }

    @Test
    void recordingStartsAtTheNextJoinAndCapturesWhatPhysicsNeeds() throws Exception {
        assertThat(packets().wantsMovementDetail()).isFalse();
        command("record", "steve", "30");
        assertThat(admin.all()).contains("starts when").contains("rejoins");
        assertThat(packets().wantsMovementDetail()).isTrue();

        out(new ClientboundPacket.JoinGame(7));
        out(new ClientboundPacket.SyncPosition(1, 8.5, 65, -3.5, 90f, 0f, 0));
        out(new ClientboundPacket.ChunkData(0, -1, -4, floor()));
        out(new ClientboundPacket.Attribute(7, "minecraft:movement_speed", 0.1,
                List.of(new ClientboundPacket.Attribute.Modifier(0.3, 2))));
        out(new ClientboundPacket.Effect(7, "minecraft:speed", 1, 200));
        out(new ClientboundPacket.Effect(99, "minecraft:levitation", 1, 200));
        out(new ClientboundPacket.EffectRemoved(7, "minecraft:speed"));
        out(new ClientboundPacket.Abilities(false, true, 0.05f));
        in(new ServerboundPacket.Input(true, false, false, false, true, false, true));
        in(new ServerboundPacket.PlayerAction("START_SPRINTING"));
        in(new ServerboundPacket.Flying(true));
        in(new ServerboundPacket.TeleportConfirm(1));
        in(new ServerboundPacket.Movement(1.5, 64, -2.25, 90f, 10f, true, true, true));
        in(new ServerboundPacket.TickEnd());
        out(new ClientboundPacket.Velocity(7, 0.1, 0.42, 0));
        out(new ClientboundPacket.Velocity(99, 9, 9, 9));
        command("record", "stop", "Steve");

        List<String> lines = savedTrace();
        assertThat(lines.get(0)).contains("\"k\":\"meta\"").contains("\"v\":2").contains("\"player\":\"Steve\"")
                .contains("\"client\":\"1.21.4\"").contains("\"entity\":7");
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"teleport\"") && l.contains("\"x\":8.5") && l.contains("\"z\":-3.5"));
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"chunk\"") && l.contains("\"x\":0,\"z\":-1,\"minSection\":-4")
                && l.contains("\"sections\":[[0,[256,1,3840,0]]]"));
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"attribute\"") && l.contains("movement_speed")
                && l.contains("\"modifiers\":[[0.3,2]]"));
        assertThat(lines).filteredOn(l -> l.contains("\"k\":\"effect\"")).hasSize(1);
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"effectRemoved\""));
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"abilities\""));
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"input\"") && l.contains("\"jump\":true")
                && l.contains("\"sprint\":true"));
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"action\"") && l.contains("START_SPRINTING"));
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"flying\""));
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"confirm\""));
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"move\"") && l.contains("\"x\":1.5") && l.contains("\"z\":-2.25"));
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"tick\""));
        assertThat(lines).filteredOn(l -> l.contains("\"k\":\"velocity\"")).hasSize(1);
        assertThat(packets().wantsMovementDetail()).isFalse();
    }

    @Test
    void anArmedRecordingCanBeCancelled() {
        command("record", "Steve");
        command("record", "stop", "Steve");
        assertThat(admin.all()).contains("cancelled");
        assertThat(packets().wantsMovementDetail()).isFalse();
        out(new ClientboundPacket.JoinGame(7));
        assertThat(packets().wantsMovementDetail()).isFalse();
    }

    @Test
    void rejectsBadDurationAndBadNames() {
        command("record", "Steve", "9999");
        command("record", "not a name!");
        command("record", "stop", "Nobody");
        assertThat(admin.all()).contains("between 5 and 300").contains("not a player name")
                .contains("is not being recorded");
    }

    @Test
    void namesAreMadeFileSafe() {
        assertThat(TraceRecorder.safe("../evil name")).isEqualTo("___evil_name");
        assertThat(TraceRecorder.safe(null)).isEqualTo("unknown");
    }
}
