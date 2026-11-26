package io.github.intruder_tech.blackdoor.core.antixray;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStrength.NORMAL;
import static io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStrength.OFF;
import static io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStrength.STRONG;
import static org.assertj.core.api.Assertions.assertThat;

class StartupSnapshotTest {

    @TempDir
    Path dir;

    private final List<String> warnings = new ArrayList<>();

    private Map<String, AntiXrayStrength> remember(long processStart, Map<String, AntiXrayStrength> current) {
        return StartupSnapshot.remember(dir.resolve("startup.properties"), processStart, current, warnings::add);
    }

    @Test
    void theSameProcessKeepsItsFirstSnapshot() {
        Map<String, AntiXrayStrength> first = Map.of("world", OFF, "my world = #1", STRONG, "other", NORMAL);
        assertThat(remember(100, first)).isEqualTo(first);
        assertThat(remember(100, Map.of("world", NORMAL, "later", NORMAL))).isEqualTo(first);
        assertThat(warnings).isEmpty();
    }

    @Test
    void aNewProcessTakesANewSnapshot() {
        remember(100, Map.of("world", OFF));
        assertThat(remember(200, Map.of("world", STRONG))).isEqualTo(Map.of("world", STRONG));
        assertThat(remember(200, Map.of("world", OFF))).isEqualTo(Map.of("world", STRONG));
    }

    @Test
    void unreadableFilesAtStartStayUnknownForTheWholeProcess() {
        assertThat(remember(100, null)).isEmpty();
        assertThat(remember(100, Map.of("world", NORMAL))).isEmpty();
    }

    @Test
    void aDamagedSnapshotMeansUnknownNotANewOne() throws IOException {
        Files.writeString(dir.resolve("startup.properties"), "something else\n");
        assertThat(remember(100, Map.of("world", NORMAL))).isEmpty();
        assertThat(warnings).isNotEmpty();
    }

    /** The previous version recorded only on or off. */
    @Test
    void theOlderFormatIsReadAndAnUnrecordedStrengthIsUnknown() throws IOException {
        Files.writeString(dir.resolve("startup.properties"),
                "# older\nprocess-start=100\nworld.world=on\nworld.world_nether=off\n");
        assertThat(remember(100, Map.of("world", STRONG, "world_nether", STRONG)))
                .isEqualTo(Map.of("world_nether", OFF));
        assertThat(warnings).isEmpty();
        assertThat(remember(200, Map.of("world", STRONG))).isEqualTo(Map.of("world", STRONG));
    }
}
