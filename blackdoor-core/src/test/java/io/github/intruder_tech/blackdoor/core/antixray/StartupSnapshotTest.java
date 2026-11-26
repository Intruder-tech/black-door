package io.github.intruder_tech.blackdoor.core.antixray;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StartupSnapshotTest {

    @TempDir
    Path dir;

    private final List<String> warnings = new ArrayList<>();

    private Map<String, Boolean> remember(long processStart, Map<String, Boolean> current) {
        return StartupSnapshot.remember(dir.resolve("startup.properties"), processStart, current, warnings::add);
    }

    @Test
    void theSameProcessKeepsItsFirstSnapshot() {
        Map<String, Boolean> first = Map.of("world", false, "my world = #1", true);
        assertThat(remember(100, first)).isEqualTo(first);
        assertThat(remember(100, Map.of("world", true, "later", true))).isEqualTo(first);
        assertThat(warnings).isEmpty();
    }

    @Test
    void aNewProcessTakesANewSnapshot() {
        remember(100, Map.of("world", false));
        assertThat(remember(200, Map.of("world", true))).isEqualTo(Map.of("world", true));
        assertThat(remember(200, Map.of("world", false))).isEqualTo(Map.of("world", true));
    }

    @Test
    void unreadableFilesAtStartStayUnknownForTheWholeProcess() {
        assertThat(remember(100, null)).isEmpty();
        assertThat(remember(100, Map.of("world", true))).isEmpty();
    }

    @Test
    void aDamagedSnapshotMeansUnknownNotANewOne() throws IOException {
        Files.writeString(dir.resolve("startup.properties"), "something else\n");
        assertThat(remember(100, Map.of("world", true))).isEmpty();
        assertThat(warnings).isNotEmpty();
    }
}
