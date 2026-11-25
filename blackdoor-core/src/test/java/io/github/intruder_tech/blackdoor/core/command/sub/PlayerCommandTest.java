package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.FakePlatform;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.state.SessionSnapshot;
import io.github.intruder_tech.blackdoor.violations.Confidence;
import io.github.intruder_tech.blackdoor.violations.Finding;
import io.github.intruder_tech.blackdoor.violations.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerCommandTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new BlackDoorEngine(FakePlatform.paper(dir), TimeSource.system());
        engine.enable();
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    private SessionSnapshot snapshot(double ping) {
        return new SessionSnapshot(id, "ItzIntruderXD", MinecraftVersion.of(26, 3), 74, 423_000, 18036, true,
                118.11, 95.0, 50.74, 461.9f, -2.7f, true, 0, 0, ping, 1, 0, 125, 0);
    }

    @Test
    void cleanPlayerCardIsFramedColouredAndReadable() {
        List<String> lines = new PlayerCommand(engine).render(snapshot(2));
        String all = String.join("\n", lines);
        assertThat(lines.get(0)).contains("Black Door").contains("ItzIntruderXD").contains("&m");
        assertThat(all).contains("&b26.3").contains("&a2 ms").contains("18,036")
                .contains("yaw 101.9").contains("&aon ground").contains("None - nothing suspicious seen");
        assertThat(lines.get(lines.size() - 1)).contains("&m");
    }

    @Test
    void pingColourReflectsQuality() {
        assertThat(String.join("", new PlayerCommand(engine).render(snapshot(120)))).contains("&e120 ms");
        assertThat(String.join("", new PlayerCommand(engine).render(snapshot(300)))).contains("&c300 ms");
        assertThat(String.join("", new PlayerCommand(engine).render(snapshot(-1)))).contains("not measured yet");
    }

    @Test
    void findingsUsePlainNames() {
        engine.ledger().orElseThrow().accept(new Finding("packet.invalid-rotation", new Subject(id, "ItzIntruderXD"),
                Confidence.DETERMINISTIC, "pitch=95", 0, false));
        String all = String.join("\n", new PlayerCommand(engine).render(snapshot(2)));
        assertThat(all).contains("1 suspicious action(s)").contains("Impossible head angle").contains("x&f1");
    }

    @Test
    void yawIsShownInFamiliarRange() {
        assertThat(PlayerCommand.wrapYaw(461.9f)).isCloseTo(101.9f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(PlayerCommand.wrapYaw(-200f)).isEqualTo(160f);
        assertThat(PlayerCommand.wrapYaw(90f)).isEqualTo(90f);
    }
}
