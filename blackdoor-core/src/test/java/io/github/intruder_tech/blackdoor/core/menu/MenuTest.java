package io.github.intruder_tech.blackdoor.core.menu;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.FakePlatform;
import io.github.intruder_tech.blackdoor.core.command.AwaitMessages;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.RecordingSender;
import io.github.intruder_tech.blackdoor.packets.CheckMode;
import io.github.intruder_tech.blackdoor.packets.PacketCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MenuTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;

    /** Minimal in-memory session that records what is shown. */
    static final class FakeSession implements MenuSession {
        final BdSender viewer;
        MenuPage page;
        Menu shown;
        final AtomicInteger refreshes = new AtomicInteger();
        boolean closed;

        FakeSession(BdSender viewer, MenuPage page) {
            this.viewer = viewer;
            show(page);
        }

        @Override
        public BdSender viewer() {
            return viewer;
        }

        @Override
        public synchronized void show(MenuPage newPage) {
            page = newPage;
            shown = newPage.build(this);
        }

        @Override
        public synchronized void refresh() {
            shown = page.build(this);
            // Counted last: the test thread reads "shown" as soon as the count changes.
            refreshes.incrementAndGet();
        }

        @Override
        public void close() {
            closed = true;
        }

        void click(int slot) {
            shown.itemAt(slot).orElseThrow().action().onClick(this);
        }
    }

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
    void mainMenuShowsRealTogglesAndLockedFutureFeatures() {
        FakeSession s = new FakeSession(RecordingSender.admin(), engine.menus().main());
        assertThat(s.shown.title()).isEqualTo("Black Door");
        assertThat(s.shown.itemAt(10).orElseThrow().name()).contains("Protection").contains("OFF");
        assertThat(s.shown.items()).filteredOn(i -> i.name().contains("coming soon"))
                .allSatisfy(i -> assertThat(i.action()).isNull())
                .anySatisfy(i -> assertThat(i.name()).contains("X-Ray"));
        assertThat(s.shown.itemAt(4)).as("setup reminder").isPresent();
    }

    @Test
    void clickingACheckCyclesItsModeAndRefreshes() throws Exception {
        RecordingSender admin = RecordingSender.admin();
        FakeSession s = new FakeSession(admin, engine.menus().main());
        s.click(12);
        assertThat(s.shown.title()).isEqualTo("Black Door - Cheat checks");
        assertThat(s.shown.items()).hasSize(PacketCheck.values().length + 2);
        s.click(10);
        AwaitMessages.await(admin, m -> m.contains("is now Block"));
        assertThat(engine.packetSettings().mode(PacketCheck.values()[0])).isEqualTo(CheckMode.MITIGATE);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (s.refreshes.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(s.refreshes.get()).isPositive();
        assertThat(s.shown.itemAt(10).orElseThrow().glow()).as("blocking checks glow").isTrue();
    }

    @Test
    void statusItemShowsUnreadablePackets() {
        FakeSession s = new FakeSession(RecordingSender.admin(), engine.menus().main());
        assertThat(s.shown.itemAt(30).orElseThrow().lore()).anyMatch(l -> l.contains("Unreadable packets"));
    }

    @Test
    void checkIconsAreCentredAndStayOffTheEdges() {
        FakeSession s = new FakeSession(RecordingSender.admin(), engine.menus().packetChecks());
        assertThat(s.shown.items())
                .filteredOn(i -> i.slot() < 27)
                .extracting(MenuItem::slot)
                .containsExactly(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
    }

    @Test
    void everyCheckHasItsOwnItem() {
        FakeSession s = new FakeSession(RecordingSender.admin(), engine.menus().packetChecks());
        assertThat(s.shown.items())
                .filteredOn(i -> i.slot() < 27)
                .extracting(MenuItem::icon)
                .doesNotHaveDuplicates()
                .doesNotContain(Icon.CHECK_DISABLED);
    }

    @Test
    void viewersWithoutEditPermissionCannotChangeSettings() {
        RecordingSender viewer = new RecordingSender("Mod", Set.of("blackdoor.command.gui"));
        FakeSession s = new FakeSession(viewer, engine.menus().packetChecks());
        s.click(10);
        assertThat(viewer.all()).contains("not allowed to change settings");
        assertThat(engine.packetSettings().mode(PacketCheck.values()[0])).isEqualTo(CheckMode.MONITOR);
    }

    @Test
    void globalModeToggle() throws Exception {
        RecordingSender admin = RecordingSender.admin();
        FakeSession s = new FakeSession(admin, engine.menus().main());
        s.click(10);
        AwaitMessages.await(admin, m -> m.contains("Protection is now ON"));
        assertThat(engine.settings().mode().name()).isEqualTo("ENFORCE");
    }

    @Test
    void closeButtonCloses() {
        FakeSession s = new FakeSession(RecordingSender.admin(), engine.menus().main());
        s.click(40);
        assertThat(s.closed).isTrue();
    }

    @Test
    void loginSystemCanBeSwitchedOnInGame() throws Exception {
        RecordingSender admin = RecordingSender.admin();
        FakeSession s = new FakeSession(admin, engine.menus().main());
        assertThat(engine.authSettings().enabled()).isFalse();
        s.click(24);
        AwaitMessages.await(admin, m -> m.contains("Login system is now On"));
        assertThat(engine.authSettings().enabled()).isTrue();
    }

    @Test
    void statusItemShowsBotFloodModeKnownAddressesAndRefusals() {
        FakeSession s = new FakeSession(RecordingSender.admin(), engine.menus().main());
        assertThat(s.shown.itemAt(30).orElseThrow().lore())
                .anyMatch(l -> l.contains("Bot flood mode") && l.contains("idle"))
                .anyMatch(l -> l.contains("Known addresses") && l.contains("refused"));
    }

    @Test
    void botFloodModeCanBeSwitchedInGameOnAnOlderNetworkFile() throws Exception {
        Path file = dir.resolve("network.yml");
        Files.writeString(file, Files.readString(file).replaceAll("(?s)\\nadaptive:.*?calm-seconds: \\d+", ""));
        assertThat(engine.reloadConfiguration("test").success()).isTrue();
        RecordingSender admin = RecordingSender.admin();
        FakeSession s = new FakeSession(admin, engine.menus().main());
        assertThat(s.shown.itemAt(22).orElseThrow().name()).contains("Bot flood mode").contains("Watch");
        s.click(22);
        AwaitMessages.await(admin, m -> m.contains("Bot flood mode is now Block"));
        assertThat(engine.networkSettings().adaptive().mode().name()).isEqualTo("MITIGATE");
    }
}
