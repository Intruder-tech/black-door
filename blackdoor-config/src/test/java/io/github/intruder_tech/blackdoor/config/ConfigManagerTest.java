package io.github.intruder_tech.blackdoor.config;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigManagerTest {

    enum Mode { MONITOR, ENFORCE }

    record Sample(String name, int threads, Mode mode) {
    }

    /** Version 1 used key "workers"; version 2 renamed it to "threads". */
    static final ConfigMigration RENAME_WORKERS = new ConfigMigration() {
        @Override
        public int fromVersion() {
            return 1;
        }

        @Override
        public void migrate(java.util.Map<String, Object> root) {
            if (root.containsKey("workers")) {
                root.put("threads", root.remove("workers"));
            }
        }
    };

    static final ConfigFileSpec<Sample> SPEC = new ConfigFileSpec<>("sample.yml", 2, "defaults/sample.yml",
            List.of(RENAME_WORKERS),
            r -> new Sample(r.getString("name", "default-name", 32, null),
                    r.getInt("threads", 2, 1, 8),
                    r.getEnum("mode", Mode.class, Mode.MONITOR)));

    @TempDir
    Path dir;

    private final ManualTimeSource time = new ManualTimeSource();

    private ConfigManager manager() {
        return new ConfigManager(dir, List.of(SPEC), getClass().getClassLoader(), time);
    }

    private void write(String content) throws Exception {
        Files.writeString(dir.resolve("sample.yml"), content, StandardCharsets.UTF_8);
    }

    @Test
    void createsDefaultFileOnFirstLoad() {
        ReloadResult result = manager().load();
        assertThat(result.success()).isTrue();
        assertThat(dir.resolve("sample.yml")).exists();
        assertThat(result.notes()).anyMatch(n -> n.contains("created default"));
    }

    @Test
    void invalidReloadKeepsPreviousSnapshot() throws Exception {
        ConfigManager manager = manager();
        write("config-version: 2\nname: first\nthreads: 3\n");
        assertThat(manager.load().success()).isTrue();
        ConfigSnapshot before = manager.current();

        write("config-version: 2\nname: second\nthreads: 500\n");
        ReloadResult result = manager.load();

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).extracting(ConfigError::path).containsExactly("threads");
        assertThat(manager.current()).isSameAs(before);
        assertThat(manager.current().get(SPEC).name()).isEqualTo("first");
    }

    @Test
    void invalidFirstLoadFallsBackToBundledDefaults() throws Exception {
        write("config-version: 2\nthreads: not-a-number\n");
        ReloadResult result = manager().load();
        assertThat(result.success()).isFalse();
        assertThat(result.usingDefaults()).isTrue();
    }

    @Test
    void defaultsSnapshotIsMarked() throws Exception {
        write("config-version: 2\nthreads: [1, 2]\n");
        ConfigManager manager = manager();
        manager.load();
        assertThat(manager.current().defaultsOnly()).isTrue();
        assertThat(manager.current().get(SPEC).threads()).isEqualTo(2);
    }

    @Test
    void refusesNewerVersion() throws Exception {
        write("config-version: 3\n");
        ReloadResult result = manager().load();
        assertThat(result.success()).isFalse();
        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e.message()).contains("newer"));
    }

    @Test
    void missingVersionIsAnError() throws Exception {
        write("name: x\n");
        assertThat(manager().load().errors()).extracting(ConfigError::path).containsExactly("config-version");
    }

    @Test
    void migratesOldVersionAndKeepsBackup() throws Exception {
        write("config-version: 1\nname: old\nworkers: 4\n");
        ConfigManager manager = manager();
        ReloadResult result = manager.load();
        assertThat(result.success()).isTrue();
        assertThat(manager.current().get(SPEC).threads()).isEqualTo(4);
        assertThat(dir.resolve("sample.yml.bak-v1")).exists();
        assertThat(Files.readString(dir.resolve("sample.yml"))).contains("config-version: 2").contains("threads: 4");
    }

    @Test
    void reportsChangedFiles() throws Exception {
        ConfigManager manager = manager();
        write("config-version: 2\nname: a\n");
        manager.load();
        assertThat(manager.load().changedFiles()).isEmpty();
        write("config-version: 2\nname: b\n");
        assertThat(manager.load().changedFiles()).containsExactly("sample.yml");
    }

    @Test
    void unknownKeysAreWarningsNotErrors() throws Exception {
        write("config-version: 2\nthraeds: 3\n");
        ReloadResult result = manager().load();
        assertThat(result.success()).isTrue();
        assertThat(result.warnings()).extracting(ConfigError::path).containsExactly("thraeds");
    }

    @Test
    void rejectsUnsafeFileNames() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new ConfigFileSpec<>("../evil.yml", 1, "x", List.of(), r -> "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
