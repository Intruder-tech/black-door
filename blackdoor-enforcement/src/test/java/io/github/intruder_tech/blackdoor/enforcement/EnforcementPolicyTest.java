package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.violations.Confidence;
import io.github.intruder_tech.blackdoor.violations.Finding;
import io.github.intruder_tech.blackdoor.violations.Subject;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static io.github.intruder_tech.blackdoor.enforcement.EnforcementSettings.Mode.DISABLED;
import static io.github.intruder_tech.blackdoor.enforcement.EnforcementSettings.Mode.KICK;
import static io.github.intruder_tech.blackdoor.enforcement.EnforcementSettings.Mode.WATCH;
import static org.assertj.core.api.Assertions.assertThat;

class EnforcementPolicyTest {

    private static final Subject PLAYER = new Subject(UUID.randomUUID(), "tester");

    private EnforcementSettings settings = new EnforcementSettings(KICK, 3, 60);
    private final EnforcementPolicy policy = new EnforcementPolicy(() -> settings, 100);

    private Optional<EnforcementPolicy.Decision> blocked(Confidence confidence, long seconds) {
        return policy.accept(new Finding("packet.sign", PLAYER, confidence, "x", seconds * 1000, true));
    }

    private Optional<EnforcementPolicy.Decision> watched(long seconds) {
        return policy.accept(new Finding("packet.sign", PLAYER, Confidence.DETERMINISTIC, "x", seconds * 1000, false));
    }

    @Test
    void kicksOnceEnoughBlockedFindingsFallInsideTheWindow() {
        assertThat(blocked(Confidence.DETERMINISTIC, 0)).isEmpty();
        assertThat(blocked(Confidence.HIGH, 1)).isEmpty();
        assertThat(blocked(Confidence.DETERMINISTIC, 2))
                .contains(new EnforcementPolicy.Decision(PLAYER, "packet.sign", 3, true));
        assertThat(blocked(Confidence.DETERMINISTIC, 3)).as("count starts again").isEmpty();
    }

    @Test
    void oldFindingsNoLongerCount() {
        blocked(Confidence.DETERMINISTIC, 0);
        blocked(Confidence.DETERMINISTIC, 1);
        assertThat(blocked(Confidence.DETERMINISTIC, 61)).isEmpty();
    }

    @Test
    void guessesNeverPunish() {
        for (int i = 0; i < 50; i++) {
            assertThat(blocked(Confidence.HEURISTIC, i)).isEmpty();
            assertThat(blocked(Confidence.STATISTICAL, i)).isEmpty();
        }
    }

    @Test
    void checksThatOnlyWatchNeverCauseAKick() {
        for (int i = 0; i < 10; i++) {
            assertThat(watched(i)).isEmpty();
        }
    }

    @Test
    void watchModeReportsWhatWouldHaveHappenedWithoutKicking() {
        settings = new EnforcementSettings(WATCH, 2, 60);
        watched(0);
        assertThat(watched(1)).contains(new EnforcementPolicy.Decision(PLAYER, "packet.sign", 2, false));
    }

    @Test
    void findingsSeenWhileWatchingDoNotCountTowardsAKickLater() {
        settings = new EnforcementSettings(WATCH, 3, 60);
        watched(0);
        watched(1);
        settings = new EnforcementSettings(KICK, 3, 60);
        assertThat(blocked(Confidence.DETERMINISTIC, 2)).isEmpty();
    }

    @Test
    void doesNothingWhenSwitchedOff() {
        settings = new EnforcementSettings(DISABLED, 1, 60);
        assertThat(blocked(Confidence.DETERMINISTIC, 0)).isEmpty();
    }

    @Test
    void ignoresFindingsWithNoPlayerId() {
        settings = new EnforcementSettings(KICK, 1, 60);
        Finding anonymous = new Finding("network.join", new Subject(null, "x"), Confidence.DETERMINISTIC, "x", 0, true);
        assertThat(policy.accept(anonymous)).isEmpty();
    }

    @Test
    void shipsSwitchedOff() {
        assertThat(EnforcementSettings.defaults().mode()).isEqualTo(DISABLED);
    }
}
