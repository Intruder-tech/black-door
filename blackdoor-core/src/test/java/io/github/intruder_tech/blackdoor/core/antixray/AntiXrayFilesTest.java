package io.github.intruder_tech.blackdoor.core.antixray;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStrength.NORMAL;
import static io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStrength.OFF;
import static io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStrength.STRONG;
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
        assertThat(files.set(NORMAL)).containsExactly(defaults);
        assertThat(Files.readString(defaults))
                .isEqualTo(DEFAULTS.replace("    enabled: false  # turned", "    enabled: true  # turned"));
    }

    @Test
    void keepsWindowsLineEndings() throws IOException {
        String windows = DEFAULTS.replace("\n", "\r\n");
        Files.writeString(defaults, windows);
        files.set(NORMAL);
        assertThat(Files.readString(defaults))
                .isEqualTo(windows.replace("    enabled: false  # turned", "    enabled: true  # turned"));
    }

    @Test
    void worldFileIsEditedOnlyWhereTheKeyAlreadyExists() throws IOException {
        Files.writeString(overworld, UNRELATED);
        Files.writeString(nether, OVERRIDE);
        assertThat(files.set(NORMAL)).containsExactlyInAnyOrder(defaults, nether);
        assertThat(Files.readString(overworld)).isEqualTo(UNRELATED);
        assertThat(backup(overworld)).doesNotExist();
        assertThat(Files.readString(nether))
                .isEqualTo(OVERRIDE.replace("    enabled: false\n", "    enabled: true\n")
                        .replace("engine-mode: 2", "engine-mode: 1"));
    }

    @Test
    void worldWithoutAFileGetsNone() throws IOException {
        files.set(NORMAL);
        assertThat(overworld).doesNotExist();
        assertThat(nether).doesNotExist();
    }

    @Test
    void backupIsMadeOnceFromTheOriginal() throws IOException {
        Files.writeString(nether, OVERRIDE);
        files.set(NORMAL);
        files.set(OFF);
        files.set(NORMAL);
        assertThat(backup(defaults)).hasContent(DEFAULTS);
        assertThat(backup(nether)).hasContent(OVERRIDE);
        try (var list = Files.list(defaults.getParent())) {
            assertThat(list.map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder("paper-world-defaults.yml", "paper-world-defaults.yml.blackdoor-backup");
        }
    }

    @Test
    void nothingToChangeWritesNothing() throws IOException {
        assertThat(files.set(OFF)).isEmpty();
        assertThat(backup(defaults)).doesNotExist();
    }

    @Test
    void missingDefaultsFileIsAnErrorAndNothingChanges() throws IOException {
        Files.writeString(nether, OVERRIDE);
        Files.delete(defaults);
        assertThatThrownBy(() -> files.set(NORMAL)).isInstanceOf(IOException.class)
                .hasMessageContaining("paper-world-defaults.yml");
        assertThat(defaults).doesNotExist();
        assertThat(Files.readString(nether)).isEqualTo(OVERRIDE);
        assertThat(files.worldStates()).isNull();
    }

    @Test
    void missingKeyInDefaultsIsAnError() throws IOException {
        Files.writeString(defaults, UNRELATED);
        assertThatThrownBy(() -> files.set(NORMAL)).isInstanceOf(IOException.class);
        assertThat(Files.readString(defaults)).isEqualTo(UNRELATED);
    }

    @Test
    void aBrokenWorldFileStopsTheWholeChange() throws IOException {
        Files.writeString(nether, "anticheat: [unclosed\n");
        assertThatThrownBy(() -> files.set(NORMAL)).isInstanceOf(IOException.class)
                .hasMessageContaining("world_nether");
        assertThat(Files.readString(defaults)).isEqualTo(DEFAULTS);
    }

    @Test
    void aLayoutThatCannotBeEditedStopsTheWholeChange() throws IOException {
        String inline = "anticheat:\n  anti-xray: {enabled: false}\n";
        Files.writeString(nether, inline);
        assertThatThrownBy(() -> files.set(NORMAL)).isInstanceOf(IOException.class);
        assertThat(Files.readString(defaults)).isEqualTo(DEFAULTS);
        assertThat(Files.readString(nether)).isEqualTo(inline);
    }

    @Test
    void aFailedWriteRestoresTheFilesAlreadyWritten() throws IOException {
        Files.writeString(nether, OVERRIDE);
        // a folder where the temporary file belongs makes writing the world file fail
        Files.createDirectories(nether.resolveSibling("paper-world.yml.blackdoor-tmp").resolve("in-the-way"));
        assertThatThrownBy(() -> files.set(NORMAL)).isInstanceOf(IOException.class);
        assertThat(Files.readString(defaults)).isEqualTo(DEFAULTS);
        assertThat(Files.readString(nether)).isEqualTo(OVERRIDE);
    }

    @Test
    void reportsEachWorld() throws IOException {
        assertThat(files.worldStates()).containsExactly(Map.entry("world", OFF), Map.entry("world_nether", OFF));
        Files.writeString(overworld, UNRELATED);
        Files.writeString(nether, OVERRIDE.replace("enabled: false\nspawn", "enabled: true\nspawn"));
        assertThat(files.worldStates()).containsExactly(Map.entry("world", OFF), Map.entry("world_nether", STRONG));
        files.set(NORMAL);
        assertThat(files.worldStates()).containsExactly(Map.entry("world", NORMAL), Map.entry("world_nether", NORMAL));
        files.set(STRONG);
        assertThat(files.worldStates()).containsExactly(Map.entry("world", STRONG), Map.entry("world_nether", STRONG));
        files.set(OFF);
        assertThat(files.worldStates()).containsExactly(Map.entry("world", OFF), Map.entry("world_nether", OFF));
    }

    private static String defaultsWith(boolean enabled, int mode) {
        return DEFAULTS.replace("    enabled: false  # turned", "    enabled: " + enabled + "  # turned")
                .replace("engine-mode: 1", "engine-mode: " + mode);
    }

    @Test
    void strongTurnsItOnWithEngineModeTwo() throws IOException {
        files.set(STRONG);
        assertThat(Files.readString(defaults)).isEqualTo(defaultsWith(true, 2));
    }

    @Test
    void offLeavesTheEngineModeAlone() throws IOException {
        files.set(STRONG);
        files.set(OFF);
        assertThat(Files.readString(defaults)).isEqualTo(defaultsWith(false, 2));
    }

    @Test
    void engineModeThreeCountsAsStrongAndOnlyNormalRewritesIt() throws IOException {
        Files.writeString(defaults, defaultsWith(true, 3));
        assertThat(files.worldStates()).containsEntry("world", STRONG);
        assertThat(files.set(STRONG)).isEmpty();
        files.set(OFF);
        files.set(STRONG);
        assertThat(Files.readString(defaults)).isEqualTo(defaultsWith(true, 3));
        files.set(NORMAL);
        assertThat(Files.readString(defaults)).isEqualTo(defaultsWith(true, 1));
    }

    @Test
    void aWorldFilesEngineModeIsEditedOnlyWhereItExists() throws IOException {
        String modeOnly = "anticheat:\n  anti-xray:\n    engine-mode: 1  # own mode\n";
        String enabledOnly = "anticheat:\n  anti-xray:\n    enabled: false\n";
        Files.writeString(overworld, modeOnly);
        Files.writeString(nether, enabledOnly);
        files.set(STRONG);
        assertThat(Files.readString(overworld)).isEqualTo(modeOnly.replace("engine-mode: 1", "engine-mode: 2"));
        assertThat(Files.readString(nether)).isEqualTo(enabledOnly.replace("enabled: false", "enabled: true"));
    }

    @Test
    void withoutAnEngineModeInTheDefaultsOnlyOffWorks() throws IOException {
        String noMode = "anticheat:\n  anti-xray:\n    enabled: true\n";
        Files.writeString(defaults, noMode);
        Files.writeString(nether, OVERRIDE);
        for (AntiXrayStrength wanted : new AntiXrayStrength[]{NORMAL, STRONG}) {
            assertThatThrownBy(() -> files.set(wanted)).isInstanceOf(IOException.class)
                    .hasMessageContaining("engine-mode").hasMessageContaining("paper-world-defaults.yml");
        }
        assertThat(Files.readString(defaults)).isEqualTo(noMode);
        assertThat(Files.readString(nether)).isEqualTo(OVERRIDE);
        files.set(OFF);
        assertThat(Files.readString(defaults)).isEqualTo(noMode.replace("true", "false"));
    }

    @Test
    void keepsMixedLineEndings() throws IOException {
        String mixed = "# Paper header\r\n# more\r\nanticheat:\n  anti-xray:\n    enabled: false\n    engine-mode: 1\n";
        Files.writeString(defaults, mixed);
        files.set(NORMAL);
        assertThat(Files.readString(defaults)).isEqualTo(mixed.replace("enabled: false", "enabled: true"));
    }

    @Test
    void aFailedMoveLeavesNoTemporaryFile() throws IOException {
        Path target = Files.createDirectories(root.resolve("taken").resolve("inside")).getParent();
        assertThatThrownBy(() -> AntiXrayFiles.writeAtomically(target, "text")).isInstanceOf(IOException.class);
        assertThat(target.resolveSibling("taken.blackdoor-tmp")).doesNotExist();
    }
}
