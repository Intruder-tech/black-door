package io.github.intruder_tech.blackdoor.core.command;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.FakePlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class ReloadCommandTest {

    @TempDir
    Path dir;

    private final ManualTimeSource time = new ManualTimeSource();
    private BlackDoorEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BlackDoorEngine(FakePlatform.paper(dir), time);
        engine.enable();
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    @Test
    void successfulReloadReportsChanges() throws Exception {
        Files.writeString(dir.resolve("config.yml"), "config-version: 1\nprofile: smp\n");
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"reload"});
        awaitMessage(sender, m -> m.contains("Configuration reloaded"));
        assertThat(sender.all()).contains("config.yml");
        assertThat(engine.settings().profile().name()).isEqualTo("SMP");
    }

    @Test
    void invalidReloadKeepsPreviousConfiguration() throws Exception {
        Files.writeString(dir.resolve("config.yml"), "config-version: 1\nworkers:\n  threads: 99\n");
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"reload"});
        // The summary line and the error lines are sent one after another by the worker; wait for the last.
        awaitMessage(sender, m -> m.contains("workers.threads"));
        assertThat(sender.all()).contains("Reload failed").contains("previous configuration kept");
        assertThat(engine.settings().workerThreads()).isEqualTo(1);
    }

    @Test
    void cooldownBlocksRapidReloads() throws Exception {
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"reload"});
        awaitMessage(sender, m -> m.contains("Configuration reloaded"));
        engine.commands().dispatch(sender, new String[]{"reload"});
        assertThat(sender.messages).last().asString().contains("cooldown");
        time.advance(Duration.ofSeconds(6));
        engine.commands().dispatch(sender, new String[]{"reload"});
        awaitMessage(sender, m -> m.contains("no changes"));
    }

    @Test
    void reloadIsAudited() throws Exception {
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"reload"});
        awaitMessage(sender, m -> m.contains("Configuration reloaded"));
        Path auditDir = dir.resolve("audit");
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        String content = "";
        while (System.nanoTime() < deadline) {
            if (Files.isDirectory(auditDir)) {
                try (var files = Files.list(auditDir)) {
                    content = files.map(ReloadCommandTest::read).reduce("", String::concat);
                }
                if (content.contains("config.reload")) {
                    break;
                }
            }
            Thread.sleep(20);
        }
        assertThat(content).contains("\"actor\":\"Admin\"").contains("\"action\":\"config.reload\"").contains("\"result\":\"success\"");
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private static void awaitMessage(RecordingSender sender, Predicate<String> match) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (sender.messages.stream().anyMatch(match)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("expected message not received; got: " + sender.messages);
    }
}
