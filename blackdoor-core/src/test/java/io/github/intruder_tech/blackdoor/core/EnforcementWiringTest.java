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
    void repeatedKicksBanForAWhileAndStaffCanLiftIt() {
        set("mode", "kick");
        set("ban-after-kicks", "2");
        set("ban-minutes", "15");
        blockedFindings(5);
        assertThat(engine.banMessage(id)).isEmpty();
        blockedFindings(5);
        assertThat(platform.kicked).last().asString().contains("Banned by Black Door for 15 minutes");
        assertThat(engine.banMessage(id)).hasValueSatisfying(m -> assertThat(m).contains("15 minutes"));

        io.github.intruder_tech.blackdoor.core.command.RecordingSender staff =
                new io.github.intruder_tech.blackdoor.core.command.RecordingSender("Mod",
                        java.util.Set.of("blackdoor.command.bans"));
        engine.commands().dispatch(staff, new String[]{"bans"});
        assertThat(staff.all()).contains("tester").contains("15 minutes");
        engine.commands().dispatch(staff, new String[]{"bans", "unban", "TESTER"});
        assertThat(staff.all()).contains("Unbanned");
        assertThat(engine.banMessage(id)).isEmpty();
    }

    @Test
    void watchModeOnlyLogs() {
        set("mode", "watch");
        blockedFindings(5);
        assertThat(platform.kicked).isEmpty();
        assertThat(platform.logged("INFO", "Would kick tester")).isTrue();
    }
}
