package io.github.intruder_tech.blackdoor.config;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigApplyChangeTest {

    @TempDir
    Path dir;

    private ConfigManager manager() {
        return new ConfigManager(dir, List.of(ConfigManagerTest.SPEC), getClass().getClassLoader(), new ManualTimeSource());
    }

    @Test
    void validChangeIsWrittenWithCommentsAndApplied() throws Exception {
        Files.writeString(dir.resolve("sample.yml"), "# keep me\nconfig-version: 2\nthreads: 2 # workers\nmode: monitor\n");
        ConfigManager manager = manager();
        manager.load();
        ReloadResult result = manager.applyChange(ConfigManagerTest.SPEC, "threads", "4");
        assertThat(result.success()).isTrue();
        assertThat(manager.current().get(ConfigManagerTest.SPEC).threads()).isEqualTo(4);
        assertThat(Files.readString(dir.resolve("sample.yml"))).contains("# keep me").contains("threads: 4  # workers");
    }

    @Test
    void invalidChangeIsRejectedAndFileUntouched() throws Exception {
        String original = "config-version: 2\nthreads: 2\nmode: monitor\n";
        Files.writeString(dir.resolve("sample.yml"), original);
        ConfigManager manager = manager();
        manager.load();
        ReloadResult result = manager.applyChange(ConfigManagerTest.SPEC, "threads", "99");
        assertThat(result.success()).isFalse();
        assertThat(result.errors()).extracting(ConfigError::path).contains("threads");
        assertThat(Files.readString(dir.resolve("sample.yml"))).isEqualTo(original);
        assertThat(manager.current().get(ConfigManagerTest.SPEC).threads()).isEqualTo(2);
    }

    @Test
    void enumValuesAreValidated() throws Exception {
        Files.writeString(dir.resolve("sample.yml"), "config-version: 2\nmode: monitor\n");
        ConfigManager manager = manager();
        manager.load();
        assertThat(manager.applyChange(ConfigManagerTest.SPEC, "mode", "enforce").success()).isTrue();
        assertThat(manager.applyChange(ConfigManagerTest.SPEC, "mode", "chaos").success()).isFalse();
        assertThat(manager.current().get(ConfigManagerTest.SPEC).mode()).isEqualTo(ConfigManagerTest.Mode.ENFORCE);
    }
}
