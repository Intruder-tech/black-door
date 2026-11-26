package io.github.intruder_tech.blackdoor.core.command;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.FakePlatform;
import io.github.intruder_tech.blackdoor.core.antixray.AntiXrayFiles;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What /blackdoor setup says about Paper's anti-xray. */
class AntiXraySetupTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.disable();
        }
    }

    private Path defaults(boolean enabled) throws IOException {
        Path file = dir.resolve("server").resolve("config").resolve("paper-world-defaults.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "anticheat:\n  anti-xray:\n    enabled: " + enabled + "\n");
        return file;
    }

    private void start(PlatformType type) {
        FakePlatform platform = new FakePlatform(dir.resolve("data"), type, MinecraftVersion.of(1, 21, 4), true);
        Path server = dir.resolve("server");
        platform.antiXray = new AntiXrayFiles(server, Map.of("world", server.resolve("world")));
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
    }

    private String antiXrayLine() throws Exception {
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"setup"});
        AwaitMessages.await(sender, m -> m.contains("Setup complete"));
        return sender.messages.stream().filter(m -> m.toLowerCase(java.util.Locale.ROOT).contains("anti-xray"))
                .findFirst().orElseThrow();
    }

    @Test
    void onAndRunningPasses() throws Exception {
        defaults(true);
        start(PlatformType.PAPER);
        assertThat(antiXrayLine()).contains("OK").contains("anti-xray is on in every world");
    }

    @Test
    void offNamesTheWorldsAndPointsToThePanel() throws Exception {
        defaults(false);
        start(PlatformType.PAPER);
        assertThat(antiXrayLine()).contains("WARN").contains("off in: world")
                .contains("Anti X-Ray item in /blackdoor gui").contains("restart")
                .doesNotContain("since the server started");
    }

    @Test
    void switchedOnButNotRestartedIsNotReportedAsOn() throws Exception {
        defaults(false);
        start(PlatformType.PAPER);
        defaults(true);
        assertThat(antiXrayLine()).contains("WARN").contains("not running yet").contains("restart")
                .doesNotContain("on in every world");
    }

    @Test
    void switchedOffButNotRestartedSaysSo() throws Exception {
        defaults(true);
        start(PlatformType.PAPER);
        defaults(false);
        assertThat(antiXrayLine()).contains("WARN").contains("off in: world").contains("since the server started");
    }

    @Test
    void notPaperEvenWithALeftoverFile() throws Exception {
        defaults(true);
        start(PlatformType.SPIGOT);
        assertThat(antiXrayLine()).contains("Paper only").doesNotContain("on in every world");
    }

    @Test
    void unreadableFilesOnPaperAreSaidToBeUnreadable() throws Exception {
        Files.writeString(defaults(true), "anticheat: [unclosed\n");
        start(PlatformType.PAPER);
        assertThat(antiXrayLine()).contains("could not be read").doesNotContain("Paper only");
    }
}
