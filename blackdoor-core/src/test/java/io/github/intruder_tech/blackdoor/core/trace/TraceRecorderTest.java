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
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TraceRecorderTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;
    private final ConnectionInfo conn = new ConnectionInfo(new Object(), UUID.randomUUID(), "Steve", MinecraftVersion.of(1, 21, 4));

    @BeforeEach
    void setUp() {
        engine = new BlackDoorEngine(FakePlatform.paper(dir), TimeSource.system());
        engine.enable();
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    @Test
    void recordsMovementAndServerEventsThenSavesFile() throws Exception {
        var packets = engine.packetEngine().orElseThrow();
        packets.onOutbound(conn, new ClientboundPacket.JoinGame(7));
        RecordingSender admin = new RecordingSender("Admin", Set.of("blackdoor.command.record"));
        engine.commands().dispatch(admin, new String[]{"record", "Steve", "30"});
        assertThat(admin.all()).contains("Recording");
        packets.onInbound(conn, ConnectionState.PLAY, 30, new ServerboundPacket.Movement(1.5, 64, -2.25, 90f, 10f, true, true, true));
        packets.onOutbound(conn, new ClientboundPacket.Velocity(7, 0.1, 0.42, 0));
        packets.onOutbound(conn, new ClientboundPacket.Velocity(99, 9, 9, 9));
        engine.commands().dispatch(admin, new String[]{"record", "stop", "Steve"});
        AwaitMessages.await(admin, m -> m.contains("saved"));
        List<Path> files;
        try (var list = Files.list(dir.resolve("traces"))) {
            files = list.toList();
        }
        assertThat(files).hasSize(1);
        List<String> lines = Files.readAllLines(files.get(0));
        assertThat(lines.get(0)).contains("\"k\":\"meta\"").contains("\"player\":\"Steve\"").contains("\"client\":\"1.21.4\"");
        assertThat(lines).anyMatch(l -> l.contains("\"k\":\"move\"") && l.contains("\"x\":1.5") && l.contains("\"z\":-2.25"));
        assertThat(lines).filteredOn(l -> l.contains("velocity")).hasSize(1);
    }

    @Test
    void rejectsBadDurationAndUnknownPlayers() {
        RecordingSender admin = new RecordingSender("Admin", Set.of("blackdoor.command.record"));
        engine.packetEngine().orElseThrow().onOutbound(conn, new ClientboundPacket.JoinGame(7));
        engine.commands().dispatch(admin, new String[]{"record", "Steve", "9999"});
        engine.commands().dispatch(admin, new String[]{"record", "Nobody"});
        assertThat(admin.all()).contains("between 5 and 300").contains("No watched player");
    }

    @Test
    void namesAreMadeFileSafe() {
        assertThat(TraceRecorder.safe("../evil name")).isEqualTo("___evil_name");
        assertThat(TraceRecorder.safe(null)).isEqualTo("unknown");
    }
}
