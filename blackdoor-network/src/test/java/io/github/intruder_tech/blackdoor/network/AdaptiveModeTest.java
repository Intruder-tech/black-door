package io.github.intruder_tech.blackdoor.network;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.violations.Finding;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Login floods from many addresses. */
class AdaptiveModeTest {

    private final ManualTimeSource time = new ManualTimeSource();
    private final List<Finding> findings = new ArrayList<>();
    private final List<Boolean> changes = new ArrayList<>();
    private final AtomicReference<NetworkSettings> settings = new AtomicReference<>(cfg(NetworkSettings.Mode.MITIGATE));
    private final AtomicBoolean enforce = new AtomicBoolean(true);
    private final AtomicBoolean forwarded = new AtomicBoolean();
    private final ConnectionGuard guard = new ConnectionGuard(findings::add, time, settings::get, enforce::get,
            forwarded::get, changes::add);
    private int next;

    /** Per-address limits out of the way; trigger 2/s (more than 20 in 10 s), allowance 1/s, calm 10 s. */
    private static NetworkSettings cfg(NetworkSettings.Mode adaptive) {
        NetworkSettings.Limit wide = new NetworkSettings.Limit(1000, 1000);
        return new NetworkSettings(NetworkSettings.Mode.MONITOR, wide, wide, wide, 255, 1000, Set.of("203.0.113.99"),
                true, Set.of(), new NetworkSettings.Adaptive(adaptive, 2, 1, 10));
    }

    private static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** A login from a new public address each time. */
    private boolean stranger() {
        next++;
        return guard.loginStart(ip("198.51." + next / 250 + "." + (next % 250 + 1)), "Bot");
    }

    private long refusedOf(int attempts) {
        return IntStream.range(0, attempts).filter(i -> stranger()).count();
    }

    @Test
    void startsAboveTheTriggerAndSharesTheAllowance() {
        assertThat(refusedOf(20)).as("at the trigger").isZero();
        assertThat(guard.attackStatus().active()).isFalse();
        assertThat(stranger()).as("21st takes the single allowance").isFalse();
        assertThat(guard.attackStatus().active()).isTrue();
        assertThat(stranger()).isTrue();
        assertThat(changes).containsExactly(true);
        assertThat(findings).extracting(Finding::checkId)
                .containsExactly("network.attack-mode", "network.unknown-login");
        assertThat(guard.attackStatus().refused()).isEqualTo(1);
    }

    @Test
    void knownAddressesStillGetIn() {
        InetAddress home = ip("203.0.113.5");
        UUID id = UUID.randomUUID();
        guard.joined(id, home);
        time.advance(Duration.ofSeconds(60));
        guard.left(id);
        refusedOf(50);
        assertThat(guard.loginStart(home, "Steve")).isFalse();
        assertThat(guard.attackStatus().knownAddresses()).isEqualTo(1);
    }

    @Test
    void endsAfterTheCalmPeriod() {
        refusedOf(30);
        time.advance(Duration.ofSeconds(9));
        assertThat(guard.attackStatus().active()).isTrue();
        time.advance(Duration.ofSeconds(2));
        assertThat(guard.attackStatus().active()).isFalse();
        assertThat(changes).containsExactly(true, false);
        assertThat(stranger()).isFalse();
    }

    @Test
    void watchModeAndWatchProtectionRefuseNothing() {
        settings.set(cfg(NetworkSettings.Mode.MONITOR));
        assertThat(refusedOf(50)).isZero();
        assertThat(guard.attackStatus().active()).isTrue();
        settings.set(cfg(NetworkSettings.Mode.MITIGATE));
        enforce.set(false);
        assertThat(refusedOf(50)).isZero();
    }

    @Test
    void refusesNothingDuringTheWarmUp() {
        NetworkSettings.Limit wide = new NetworkSettings.Limit(1000, 1000);
        settings.set(new NetworkSettings(NetworkSettings.Mode.MONITOR, wide, wide, wide, 255, 1000, Set.of(),
                true, Set.of(), new NetworkSettings.Adaptive(NetworkSettings.Mode.MITIGATE, 2, 1, 10, 300)));
        assertThat(NetworkSettings.defaults().adaptive().warmupSeconds()).isEqualTo(300);
        assertThat(refusedOf(50)).isZero();
        assertThat(guard.attackStatus().active()).as("still noticed").isTrue();
        assertThat(guard.warmingUp()).isTrue();
        time.advance(Duration.ofSeconds(300));
        assertThat(guard.warmingUp()).isFalse();
        assertThat(refusedOf(50)).isPositive();
    }

    @Test
    void switchedOffExemptAndForwardedAreNotCounted() {
        settings.set(cfg(NetworkSettings.Mode.DISABLED));
        refusedOf(50);
        settings.set(cfg(NetworkSettings.Mode.MITIGATE));
        for (int i = 0; i < 50; i++) {
            guard.loginStart(ip("203.0.113.99"), "Bot");
            guard.loginStart(ip("10.0.0.4"), "Bot");
        }
        forwarded.set(true);
        refusedOf(50);
        assertThat(guard.attackStatus().active()).isFalse();
        assertThat(findings).isEmpty();
    }

    @Test
    void oneNoisyAddressCannotUseUpTheAllowanceWhileAddressLimitsOnlyWatch() {
        NetworkSettings.Limit one = new NetworkSettings.Limit(1, 1);
        NetworkSettings.Limit wide = new NetworkSettings.Limit(1000, 1000);
        settings.set(new NetworkSettings(NetworkSettings.Mode.MONITOR, wide, wide, one, 255, 1000, Set.of(),
                true, Set.of(), new NetworkSettings.Adaptive(NetworkSettings.Mode.MITIGATE, 2, 1, 10)));
        InetAddress noisy = ip("203.0.113.7");
        for (int i = 0; i < 30; i++) {
            guard.loginStart(noisy, "Bot");
        }
        assertThat(guard.loginStart(noisy, "Bot")).as("over its own limit during an attack").isTrue();
        assertThat(stranger()).as("allowance untouched").isFalse();
    }

    @Test
    void aLoginRefusedByTheAddressLimitCountsButUsesNoAllowance() {
        NetworkSettings.Limit one = new NetworkSettings.Limit(1, 1);
        NetworkSettings.Limit wide = new NetworkSettings.Limit(1000, 1000);
        settings.set(new NetworkSettings(NetworkSettings.Mode.MITIGATE, wide, wide, one, 255, 1000, Set.of(),
                true, Set.of(), new NetworkSettings.Adaptive(NetworkSettings.Mode.MITIGATE, 2, 1, 10)));
        InetAddress noisy = ip("203.0.113.7");
        for (int i = 0; i < 30; i++) {
            guard.loginStart(noisy, "Bot");
        }
        assertThat(guard.attackStatus().active()).isTrue();
        assertThat(stranger()).as("allowance untouched").isFalse();
    }
}
