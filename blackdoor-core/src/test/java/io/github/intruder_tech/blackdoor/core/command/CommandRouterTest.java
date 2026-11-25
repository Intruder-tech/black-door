package io.github.intruder_tech.blackdoor.core.command;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.FakePlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRouterTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;
    private CommandRouter router;

    @BeforeEach
    void setUp() {
        engine = new BlackDoorEngine(FakePlatform.paper(dir), TimeSource.system());
        engine.enable();
        router = engine.commands();
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    @Test
    void noArgsShowsOnlyPermittedCommands() {
        RecordingSender sender = new RecordingSender("Mod", Set.of("blackdoor.command.status"));
        router.dispatch(sender, new String[0]);
        assertThat(sender.all()).contains("status").doesNotContain("reload").doesNotContain("diagnostics");
    }

    @Test
    void deniesWithoutPermission() {
        RecordingSender sender = new RecordingSender("Player", Set.of());
        router.dispatch(sender, new String[]{"reload"});
        assertThat(sender.all()).contains("do not have permission");
    }

    @Test
    void unknownSubcommandIsReported() {
        RecordingSender sender = RecordingSender.admin();
        router.dispatch(sender, new String[]{"nope"});
        assertThat(sender.all()).contains("Unknown subcommand");
    }

    @Test
    void rejectsOversizedInput() {
        RecordingSender sender = RecordingSender.admin();
        router.dispatch(sender, new String[]{"status", "x".repeat(65)});
        assertThat(sender.all()).contains("too long");
        String[] many = new String[17];
        java.util.Arrays.fill(many, "a");
        router.dispatch(sender, many);
        assertThat(sender.messages).hasSize(2);
    }

    @Test
    void tabCompletionIsPermissionFiltered() {
        RecordingSender sender = new RecordingSender("Mod", Set.of("blackdoor.command.status", "blackdoor.command.version"));
        assertThat(router.complete(sender, new String[]{""})).containsExactly("status", "version");
        assertThat(router.complete(sender, new String[]{"st"})).containsExactly("status");
        assertThat(router.complete(sender, new String[]{"reload", ""})).isEmpty();
    }

    @Test
    void statusVersionAndDiagnosticsRender() {
        RecordingSender sender = RecordingSender.admin();
        router.dispatch(sender, new String[]{"status"});
        router.dispatch(sender, new String[]{"version"});
        router.dispatch(sender, new String[]{"diagnostics"});
        assertThat(sender.all())
                .contains("Running normally")
                .contains("OFF &7(watch only)")
                .contains("0.0.0-test")
                .contains("Workers:")
                .contains("config.yml:");
        assertThat(sender.all()).doesNotContain(dir.toString());
    }

    @Test
    void subcommandFailureIsContained() {
        BdSender broken = new RecordingSender("X", Set.of("blackdoor.command.status")) {
            @Override
            public void sendMessage(String message) {
                if (message.contains("Status")) {
                    throw new IllegalStateException("boom");
                }
                super.sendMessage(message);
            }
        };
        router.dispatch(broken, new String[]{"status"});
        assertThat(((RecordingSender) broken).all()).contains("Internal error");
    }
}
