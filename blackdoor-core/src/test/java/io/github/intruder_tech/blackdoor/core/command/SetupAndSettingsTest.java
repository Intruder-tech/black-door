package io.github.intruder_tech.blackdoor.core.command;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.FakePlatform;
import io.github.intruder_tech.blackdoor.packets.CheckMode;
import io.github.intruder_tech.blackdoor.packets.PacketCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SetupAndSettingsTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BlackDoorEngine(FakePlatform.paper(dir), TimeSource.system());
        engine.enable();
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    @Test
    void setupReportsChecksMarksCompletedAndPointsToGui() throws Exception {
        assertThat(engine.settings().setupCompleted()).isFalse();
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"setup", "competitive-pvp"});
        AwaitMessages.await(sender, m -> m.contains("open the control panel"));
        assertThat(sender.all()).contains("[&aOK&8] &7Packet layer available").contains("Configuration valid")
                .contains("Profile set to &fcompetitive-pvp").contains("/blackdoor gui");
        assertThat(engine.settings().setupCompleted()).isTrue();
        assertThat(engine.settings().profile().name()).isEqualTo("COMPETITIVE_PVP");
        assertThat(Files.readString(dir.resolve("config.yml"))).contains("completed: true").contains("# Black Door - general settings");
    }

    @Test
    void setupRejectsUnknownProfile() {
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"setup", "chaos"});
        assertThat(sender.all()).contains("Unknown profile");
        assertThat(engine.settings().setupCompleted()).isFalse();
    }

    @Test
    void setupWorksOnConfigFilesWrittenBeforeTheSetupKeyExisted() throws Exception {
        engine.disable();
        Files.writeString(dir.resolve("config.yml"), "config-version: 1\nmode: monitor\n");
        engine = new BlackDoorEngine(FakePlatform.paper(dir), TimeSource.system());
        engine.enable();
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"setup"});
        AwaitMessages.await(sender, m -> m.contains("Setup complete"));
        assertThat(engine.settings().setupCompleted()).isTrue();
    }

    @Test
    void settingsServiceChangesPacketCheckModeAndKeepsComments() throws Exception {
        var service = engine.settingsService();
        var setting = service.find(PacketCheck.INVALID_ROTATION.id()).orElseThrow();
        assertThat(service.current(setting)).isEqualTo("monitor");
        assertThat(service.next(setting)).isEqualTo("mitigate");
        java.util.concurrent.CompletableFuture<Boolean> done = new java.util.concurrent.CompletableFuture<>();
        assertThat(service.change(setting, "mitigate", "Admin", r -> done.complete(r.success()))).isTrue();
        assertThat(done.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(engine.packetSettings().mode(PacketCheck.INVALID_ROTATION)).isEqualTo(CheckMode.MITIGATE);
        assertThat(Files.readString(dir.resolve("checks.yml"))).contains("# Pitch outside -90..90");
    }

    @Test
    void settingsServiceRejectsValuesOutsideTheOptionList() {
        var service = engine.settingsService();
        var setting = service.find("general.mode").orElseThrow();
        assertThat(service.change(setting, "off", "Admin", r -> { })).isFalse();
    }

    @Test
    void checksCommandListsEveryPacketCheck() {
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"checks"});
        for (PacketCheck check : PacketCheck.values()) {
            assertThat(sender.all()).contains(check.id());
        }
    }

    @Test
    void guiFromConsoleIsRefused() {
        RecordingSender console = new RecordingSender("console", java.util.Set.of("blackdoor.command.gui")) {
            @Override
            public boolean isConsole() {
                return true;
            }
        };
        engine.commands().dispatch(console, new String[]{"gui"});
        assertThat(console.all()).contains("Only players");
    }
}
