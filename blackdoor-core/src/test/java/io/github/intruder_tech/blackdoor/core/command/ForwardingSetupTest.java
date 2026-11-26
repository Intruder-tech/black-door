package io.github.intruder_tech.blackdoor.core.command;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.FakePlatform;
import io.github.intruder_tech.blackdoor.core.platform.SecurityFacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** What /blackdoor setup says about proxy forwarding. */
class ForwardingSetupTest {

    @TempDir
    Path dir;

    private String setup(SecurityFacts facts) throws Exception {
        FakePlatform platform = FakePlatform.paper(dir);
        platform.facts = facts;
        BlackDoorEngine engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
        try {
            RecordingSender sender = RecordingSender.admin();
            engine.commands().dispatch(sender, new String[]{"setup"});
            AwaitMessages.await(sender, m -> m.contains("Setup complete"));
            return sender.all();
        } finally {
            engine.disable();
        }
    }

    @Test
    void offlineBehindVelocityWithStrongSecretPasses() throws Exception {
        assertThat(setup(new SecurityFacts(false, false, false, true, false)))
                .contains("Velocity modern forwarding is on").doesNotContain("FAIL");
    }

    @Test
    void weakVelocitySecretFails() throws Exception {
        assertThat(setup(new SecurityFacts(false, false, false, true, true)))
                .contains("FAIL").contains("secret is empty or too short");
    }

    @Test
    void legacyForwardingOnAnOpenServerAsksForTheProxyList() throws Exception {
        assertThat(setup(new SecurityFacts(false, true, true, false, false)))
                .contains("allowed-proxies");
    }

    private static SecurityFacts antiXray(java.util.List<String> without) {
        return new SecurityFacts(true, false, false, false, false, without);
    }

    @Test
    void antiXrayOnInEveryWorldPasses() throws Exception {
        assertThat(setup(antiXray(java.util.List.of()))).contains("anti-xray is on in every world");
    }

    @Test
    void worldsWithoutAntiXrayAreNamedWithTheFix() throws Exception {
        assertThat(setup(antiXray(java.util.List.of("world", "world_nether"))))
                .contains("off in: world, world_nether").contains("paper-world-defaults.yml");
    }

    @Test
    void offlineWithoutAnyForwardingFails() throws Exception {
        assertThat(setup(new SecurityFacts(false, false, false, false, false)))
                .contains("anyone can join as any player");
    }
}
