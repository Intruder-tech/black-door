package io.github.intruder_tech.blackdoor.core.antixray;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AntiXrayFilesTest {

    private static final String DEFAULTS = """
            # Paper world defaults - hand written note
            _version: 31
            anticheat:
              anti-xray:
                enabled: false  # turned off by the owner
                engine-mode: 1
                hidden-blocks:
                - copper_ore
                - diamond_ore
              obfuscation:
                items:
                  hide-itemmeta: false
            spawn:
              enabled: false
            """;

    private static final String UNRELATED = """
            # only spawn settings here
            _version: 31
            spawn:
              keep-spawn-loaded: false
            """;

    private static final String OVERRIDE = """
            _version: 31
            # nether keeps its own setting
            anticheat:
              anti-xray:
                engine-mode: 2
                enabled: false
            spawn:
              enabled: false
            """;

    @TempDir
    Path root;

    private Path defaults;
    private Path overworld;
    private Path nether;
    private AntiXrayFiles files;

    @BeforeEach
    void setUp() throws IOException {
        defaults = root.resolve("config").resolve("paper-world-defaults.yml");
        Files.createDirectories(defaults.getParent());
        Files.writeString(defaults, DEFAULTS);
        overworld = Files.createDirectories(root.resolve("world")).resolve("paper-world.yml");
        nether = Files.createDirectories(root.resolve("world_nether")).resolve("paper-world.yml");
        Map<String, Path> worlds = new LinkedHashMap<>();
        worlds.put("world", overworld.getParent());
        worlds.put("world_nether", nether.getParent());
        files = new AntiXrayFiles(root, worlds);
    }

    private static Path backup(Path file) {
        return file.resolveSibling(file.getFileName() + ".blackdoor-backup");
    }

    @Test
    void changesOnlyTheEnabledValueAndKeepsCommentsAndOrder() throws IOException {
        assertThat(files.setEnabled(true)).containsExactly(defaults);
        assertThat(Files.readString(defaults))
                .isEqualTo(DEFAULTS.replace("    enabled: false  # turned", "    enabled: true  # turned"));
    }

    @Test
    void keepsWindowsLineEndings() throws IOException {
        String windows = DEFAULTS.replace("\n", "\r\n");
        Files.writeString(defaults, windows);
        files.setEnabled(true);
        assertThat(Files.readString(defaults))
                .isEqualTo(windows.replace("    enabled: false  # turned", "    enabled: true  # turned"));
    }

    @Test
    void worldFileIsEditedOnlyWhereTheKeyAlreadyExists() throws IOException {
        Files.writeString(overworld, UNRELATED);
        Files.writeString(nether, OVERRIDE);
        assertThat(files.setEnabled(true)).containsExactlyInAnyOrder(defaults, nether);
        assertThat(Files.readString(overworld)).isEqualTo(UNRELATED);
        assertThat(backup(overworld)).doesNotExist();
        assertThat(Files.readString(nether))
                .isEqualTo(OVERRIDE.replace("    enabled: false\n", "    enabled: true\n"));
    }

    @Test
    void worldWithoutAFileGetsNone() throws IOException {
        files.setEnabled(true);
        assertThat(overworld).doesNotExist();
        assertThat(nether).doesNotExist();
    }

    @Test
    void backupIsMadeOnceFromTheOriginal() throws IOException {
        Files.writeString(nether, OVERRIDE);
        files.setEnabled(true);
        files.setEnabled(false);
        files.setEnabled(true);
        assertThat(backup(defaults)).hasContent(DEFAULTS);
        assertThat(backup(nether)).hasContent(OVERRIDE);
        try (var list = Files.list(defaults.getParent())) {
            assertThat(list.map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder("paper-world-defaults.yml", "paper-world-defaults.yml.blackdoor-backup");
        }
    }

    @Test
    void nothingToChangeWritesNothing() throws IOException {
        assertThat(files.setEnabled(false)).isEmpty();
        assertThat(backup(defaults)).doesNotExist();
    }

    @Test
    void missingDefaultsFileIsAnErrorAndNothingChanges() throws IOException {
        Files.writeString(nether, OVERRIDE);
        Files.delete(defaults);
        assertThatThrownBy(() -> files.setEnabled(true)).isInstanceOf(IOException.class)
                .hasMessageContaining("paper-world-defaults.yml");
        assertThat(defaults).doesNotExist();
        assertThat(Files.readString(nether)).isEqualTo(OVERRIDE);
        assertThat(files.worldStates()).isNull();
    }

    @Test
    void missingKeyInDefaultsIsAnError() throws IOException {
        Files.writeString(defaults, UNRELATED);
        assertThatThrownBy(() -> files.setEnabled(true)).isInstanceOf(IOException.class);
        assertThat(Files.readString(defaults)).isEqualTo(UNRELATED);
    }

    @Test
    void aBrokenWorldFileStopsTheWholeChange() throws IOException {
        Files.writeString(nether, "anticheat: [unclosed\n");
        assertThatThrownBy(() -> files.setEnabled(true)).isInstanceOf(IOException.class)
                .hasMessageContaining("world_nether");
        assertThat(Files.readString(defaults)).isEqualTo(DEFAULTS);
    }

    @Test
    void aLayoutThatCannotBeEditedStopsTheWholeChange() throws IOException {
        String inline = "anticheat:\n  anti-xray: {enabled: false}\n";
        Files.writeString(nether, inline);
        assertThatThrownBy(() -> files.setEnabled(true)).isInstanceOf(IOException.class);
        assertThat(Files.readString(defaults)).isEqualTo(DEFAULTS);
        assertThat(Files.readString(nether)).isEqualTo(inline);
    }

    @Test
    void aFailedWriteRestoresTheFilesAlreadyWritten() throws IOException {
        Files.writeString(nether, OVERRIDE);
        // a folder where the temporary file belongs makes writing the world file fail
        Files.createDirectories(nether.resolveSibling("paper-world.yml.blackdoor-tmp").resolve("in-the-way"));
        assertThatThrownBy(() -> files.setEnabled(true)).isInstanceOf(IOException.class);
        assertThat(Files.readString(defaults)).isEqualTo(DEFAULTS);
        assertThat(Files.readString(nether)).isEqualTo(OVERRIDE);
    }

    @Test
    void reportsEachWorld() throws IOException {
        assertThat(files.worldStates()).containsExactly(Map.entry("world", false), Map.entry("world_nether", false));
        Files.writeString(overworld, UNRELATED);
        Files.writeString(nether, OVERRIDE.replace("enabled: false\nspawn", "enabled: true\nspawn"));
        assertThat(files.worldStates()).containsExactly(Map.entry("world", false), Map.entry("world_nether", true));
        files.setEnabled(true);
        assertThat(files.worldStates()).containsExactly(Map.entry("world", true), Map.entry("world_nether", true));
        files.setEnabled(false);
        assertThat(files.worldStates()).containsExactly(Map.entry("world", false), Map.entry("world_nether", false));
    }

    @Test
    void keepsMixedLineEndings() throws IOException {
        String mixed = "# Paper header\r\n# more\r\nanticheat:\n  anti-xray:\n    enabled: false\n    engine-mode: 1\n";
        Files.writeString(defaults, mixed);
        files.setEnabled(true);
        assertThat(Files.readString(defaults)).isEqualTo(mixed.replace("enabled: false", "enabled: true"));
    }

    @Test
    void aFailedMoveLeavesNoTemporaryFile() throws IOException {
        Path target = Files.createDirectories(root.resolve("taken").resolve("inside")).getParent();
        assertThatThrownBy(() -> AntiXrayFiles.writeAtomically(target, "text")).isInstanceOf(IOException.class);
        assertThat(target.resolveSibling("taken.blackdoor-tmp")).doesNotExist();
    }
}
