package io.github.intruder_tech.blackdoor.core;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.core.command.RecordingSender;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Join and leave signals from the adapters, and what staff see of adaptive mode. */
class AdaptiveWiringTest {

    @TempDir
    Path dir;

    private final ManualTimeSource time = new ManualTimeSource();
    private BlackDoorEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BlackDoorEngine(new FakePlatform(dir, PlatformType.VELOCITY, null, false), time);
        engine.enable();
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    @Test
    void aPlayerWhoStayedIsKnownAndTheStatusIsInDiagnostics() throws Exception {
        UUID id = UUID.randomUUID();
        engine.playerJoined(id, InetAddress.getByName("203.0.113.5"));
        time.advance(Duration.ofSeconds(60));
        engine.playerLeft(id);
        assertThat(engine.connectionGuard().orElseThrow().attackStatus().knownAddresses()).isEqualTo(1);
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"diagnostics"});
        assertThat(sender.all()).contains("Bot flood mode").contains("idle");
    }
}
