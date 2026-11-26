package io.github.intruder_tech.blackdoor.core.menu;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.FakePlatform;
import io.github.intruder_tech.blackdoor.core.antixray.AntiXrayFiles;
import io.github.intruder_tech.blackdoor.core.command.AwaitMessages;
import io.github.intruder_tech.blackdoor.core.command.RecordingSender;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AntiXrayMenuTest {

    private static final int SLOT = 16;

    @TempDir
    Path dir;

    private Path defaults;
    private FakePlatform platform;
    private BlackDoorEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        Path server = dir.resolve("server");
        defaults = server.resolve("config").resolve("paper-world-defaults.yml");
        Files.createDirectories(defaults.getParent());
        Files.writeString(defaults, "anticheat:\n  anti-xray:\n    enabled: false\n    engine-mode: 1\n");
        platform = FakePlatform.paper(dir.resolve("data"));
        platform.antiXray = new AntiXrayFiles(server, Map.of("world", server.resolve("world")));
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    private MenuTest.FakeSession open(RecordingSender viewer) {
        return new MenuTest.FakeSession(viewer, engine.menus().main());
    }

    private static MenuItem item(MenuTest.FakeSession session) {
        return session.shown.itemAt(SLOT).orElseThrow();
    }

    private static void clickAndWait(MenuTest.FakeSession session) throws InterruptedException {
        int before = session.refreshes.get();
        session.click(SLOT);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (session.refreshes.get() == before && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(session.refreshes.get()).isGreaterThan(before);
    }

    @Test
    void offItemExplainsPaperFeatureAndOffersToTurnOn() {
        MenuItem item = item(open(RecordingSender.admin()));
        assertThat(item.name()).contains("Anti X-Ray").contains("Off").doesNotContain("restart");
        assertThat(item.action()).isNotNull();
        assertThat(String.join(" ", item.lore()))
                .contains("Paper's own")
                .contains("Hides ores")
                .contains("engine mode is not changed")
                .contains("next server restart")
                .contains("Click to turn on");
    }

    @Test
    void togglesBackAndForthShowingConfiguredAndRunning() throws Exception {
        RecordingSender admin = RecordingSender.admin();
        MenuTest.FakeSession session = open(admin);

        clickAndWait(session);
        AwaitMessages.await(admin, m -> m.contains("turned On") && m.contains("after the next server restart"));
        assertThat(Files.readString(defaults)).contains("enabled: true").contains("engine-mode: 1");
        assertThat(item(session).name()).contains("On").contains("after restart");
        assertThat(item(session).lore())
                .anyMatch(l -> l.contains("Configured:") && l.contains("On"))
                .anyMatch(l -> l.contains("Running now:") && l.contains("Off"))
                .anyMatch(l -> l.contains("Click to turn off"));

        clickAndWait(session);
        AwaitMessages.await(admin, m -> m.contains("turned Off") && m.contains("after the next server restart"));
        assertThat(Files.readString(defaults)).contains("enabled: false");
        assertThat(item(session).name()).contains("Off").doesNotContain("after restart");
        assertThat(item(session).lore()).anyMatch(l -> l.contains("Click to turn on"));
    }

    @Test
    void changeIsAuditedWithTheActor() throws Exception {
        MenuTest.FakeSession session = open(RecordingSender.admin());
        clickAndWait(session);
        engine.disable();
        StringBuilder audit = new StringBuilder();
        try (var logs = Files.list(dir.resolve("data").resolve("audit"))) {
            for (Path log : logs.toList()) {
                audit.append(Files.readString(log));
            }
        }
        assertThat(audit.toString().lines()).anyMatch(l -> l.contains("\"actor\":\"Admin\"")
                && l.contains("antixray.set enabled=true") && l.contains("success"));
    }

    @Test
    void viewerWithoutPermissionChangesNothing() throws Exception {
        String before = Files.readString(defaults);
        RecordingSender viewer = new RecordingSender("Mod", Set.of("blackdoor.command.gui"));
        open(viewer).click(SLOT);
        assertThat(viewer.all()).contains("not allowed to change settings");
        engine.disable(); // drains the workers, so a queued write would have happened
        assertThat(Files.readString(defaults)).isEqualTo(before);
        assertThat(defaults.resolveSibling("paper-world-defaults.yml.blackdoor-backup")).doesNotExist();
    }

    @Test
    void aFailedChangeIsReportedToThePlayer() throws Exception {
        Path world = Files.createDirectories(dir.resolve("server").resolve("world"));
        Files.writeString(world.resolve("paper-world.yml"), "anticheat:\n  anti-xray: {enabled: false}\n");
        String before = Files.readString(defaults);
        RecordingSender admin = RecordingSender.admin();
        clickAndWait(open(admin));
        AwaitMessages.await(admin, m -> m.contains("was not changed") && m.contains("world/paper-world.yml"));
        assertThat(Files.readString(defaults)).isEqualTo(before);
    }

    @Test
    void unreadableFilesOnPaperAreNotClickableAndNotBlamedOnTheSoftware() throws Exception {
        Files.delete(defaults);
        MenuItem item = item(open(RecordingSender.admin()));
        assertThat(item.name()).contains("Unknown");
        assertThat(item.action()).isNull();
        assertThat(String.join(" ", item.lore())).contains("could not be read").doesNotContain("Paper only");
    }

    @Test
    void notOfferedOnSpigotEvenWithALeftoverFile() {
        engine.disable();
        FakePlatform spigot = new FakePlatform(dir.resolve("spigot-data"), PlatformType.SPIGOT,
                MinecraftVersion.of(1, 21, 4), true);
        spigot.antiXray = platform.antiXray;
        engine = new BlackDoorEngine(spigot, TimeSource.system());
        engine.enable();
        MenuItem item = item(open(RecordingSender.admin()));
        assertThat(item.name()).contains("Unknown");
        assertThat(item.action()).isNull();
        assertThat(String.join(" ", item.lore())).contains("Paper only");
    }

    @Test
    void viewerWithoutPermissionIsNotInvitedToClick() {
        MenuItem item = item(open(new RecordingSender("Mod", Set.of("blackdoor.command.gui"))));
        assertThat(item.lore()).noneMatch(l -> l.contains("Click to turn"));
    }

    @Test
    void reEnablingInTheSameProcessKeepsWhatIsRunning() throws Exception {
        clickAndWait(open(RecordingSender.admin()));
        engine.disable();
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
        MenuItem item = item(open(RecordingSender.admin()));
        assertThat(item.name()).contains("after restart");
        assertThat(item.lore()).anyMatch(l -> l.contains("Running now:") && l.contains("Off"));
        assertThat(item.glow()).isFalse();
    }

    @Test
    void aWorldLoadedLaterIsUnknownUntilRestartNeverOn() throws Exception {
        Files.writeString(defaults, Files.readString(defaults).replace("enabled: false", "enabled: true"));
        engine.disable();
        Files.deleteIfExists(dir.resolve("data").resolve("antixray-startup.properties"));
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
        assertThat(item(open(RecordingSender.admin())).lore())
                .anyMatch(l -> l.contains("Running now:") && l.contains("&aOn"));

        Path server = dir.resolve("server");
        Map<String, Path> worlds = new LinkedHashMap<>();
        worlds.put("world", server.resolve("world"));
        worlds.put("later", server.resolve("later"));
        platform.antiXray = new AntiXrayFiles(server, worlds);
        MenuItem item = item(open(RecordingSender.admin()));
        assertThat(item.name()).contains("after restart");
        assertThat(item.lore()).anyMatch(l -> l.contains("Running now:") && l.contains("Unknown until restart")
                && l.contains("later") && !l.contains("&aOn"));
        assertThat(item.glow()).isFalse();
    }

    @Test
    void anUnexpectedErrorStillAnswersAuditsAndRefreshes() throws Exception {
        Path server = dir.resolve("server");
        AtomicBoolean explode = new AtomicBoolean();
        Path real = server.resolve("world");
        Path folder = (Path) Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[]{Path.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("resolve") && explode.getAndSet(false)) {
                        throw new IllegalStateException("boom");
                    }
                    return method.invoke(real, args);
                });
        platform.antiXray = new AntiXrayFiles(server, Map.of("world", folder));
        RecordingSender admin = RecordingSender.admin();
        MenuTest.FakeSession session = open(admin);
        explode.set(true);

        clickAndWait(session);
        AwaitMessages.await(admin, m -> m.contains("was not changed"));
        engine.disable();
        StringBuilder audit = new StringBuilder();
        try (var logs = Files.list(dir.resolve("data").resolve("audit"))) {
            for (Path log : logs.toList()) {
                audit.append(Files.readString(log));
            }
        }
        assertThat(audit.toString().lines())
                .anyMatch(l -> l.contains("antixray.set enabled=true") && l.contains("failed"));
    }
}
