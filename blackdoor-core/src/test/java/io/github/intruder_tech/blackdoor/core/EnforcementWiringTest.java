package io.github.intruder_tech.blackdoor.core;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.config.CoreConfig;
import io.github.intruder_tech.blackdoor.violations.Confidence;
import io.github.intruder_tech.blackdoor.violations.Finding;
import io.github.intruder_tech.blackdoor.violations.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Findings reach the ledger and, when the owner switched it on, get the player kicked. */
class EnforcementWiringTest {

    @TempDir
    Path dir;

    private FakePlatform platform;
    private BlackDoorEngine engine;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        platform = FakePlatform.paper(dir);
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    private void set(String path, String value) {
        assertThat(engine.applyChange(CoreConfig.ENFORCEMENT, path, value, "test").success()).isTrue();
    }

    private void blockedFindings(int count) {
        for (int i = 0; i < count; i++) {
            engine.findings().accept(new Finding("packet.sign", new Subject(id, "tester"),
                    Confidence.DETERMINISTIC, "x", 1000 + i, true));
        }
    }

    @Test
    void nobodyIsKickedByDefaultButFindingsAreStillCounted() {
        blockedFindings(20);
        assertThat(platform.kicked).isEmpty();
        assertThat(engine.ledger().orElseThrow().total()).isEqualTo(20);
    }

    @Test
    void kickModeKicksWithAPlainMessage() {
        set("mode", "kick");
        blockedFindings(5);
        assertThat(platform.kicked).containsExactly(id + " Kicked by Black Door: suspicious game client.");
        assertThat(platform.logged("WARN", "Kicked tester")).isTrue();
    }

    @Test
    void watchModeOnlyLogs() {
        set("mode", "watch");
        blockedFindings(5);
        assertThat(platform.kicked).isEmpty();
        assertThat(platform.logged("INFO", "Would kick tester")).isTrue();
    }
}
