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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionGuardTest {

    private final ManualTimeSource time = new ManualTimeSource();
    private final List<Finding> findings = new ArrayList<>();
    private final AtomicReference<NetworkSettings> settings = new AtomicReference<>(small(NetworkSettings.Mode.MONITOR));
    private final AtomicBoolean enforce = new AtomicBoolean();
    private final AtomicBoolean forwarded = new AtomicBoolean();
    private final ConnectionGuard guard = new ConnectionGuard(findings::add, time, settings::get, enforce::get,
            forwarded::get);

    /** Burst of 2 for every limit, 1 per second, at most 10 tracked addresses. */
    private static NetworkSettings small(NetworkSettings.Mode mode) {
        NetworkSettings.Limit limit = new NetworkSettings.Limit(2, 1);
        return new NetworkSettings(mode, limit, limit, limit, 255, 10, Set.of("203.0.113.99"), true);
    }

    private static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private List<String> ids() {
        return findings.stream().map(Finding::checkId).toList();
    }

    @Test
    void connectionsOverTheBurstAreReportedOncePerSecondAndRefill() {
        InetAddress a = ip("203.0.113.5");
        for (int i = 0; i < 5; i++) {
            guard.handshake(a, 20);
        }
        assertThat(ids()).containsExactly("network.connection-rate");
        time.advance(Duration.ofSeconds(10));
        guard.handshake(a, 20);
        assertThat(findings).hasSize(1);
    }

    @Test
    void eachAddressHasItsOwnAllowance() {
        for (int i = 0; i < 2; i++) {
            guard.statusPing(ip("203.0.113.5"));
            guard.statusPing(ip("203.0.113.6"));
        }
        assertThat(findings).isEmpty();
        guard.statusPing(ip("203.0.113.5"));
        assertThat(ids()).containsExactly("network.ping-rate");
    }

    @Test
    void refusesOnlyWhenMitigatingAndEnforcing() {
        InetAddress a = ip("203.0.113.5");
        guard.loginStart(a, "Steve");
        guard.loginStart(a, "Steve");
        assertThat(guard.loginStart(a, "Steve")).as("monitor").isFalse();
        settings.set(small(NetworkSettings.Mode.MITIGATE));
        assertThat(guard.loginStart(a, "Steve")).as("global monitor").isFalse();
        enforce.set(true);
        assertThat(guard.loginStart(a, "Steve")).isTrue();
        assertThat(guard.refused()).isEqualTo(1);
    }

    @Test
    void privateAndExemptAddressesAreNeverRateLimited() {
        for (String exempt : List.of("127.0.0.1", "10.0.0.4", "192.168.1.9", "::1", "fd00::1", "203.0.113.99")) {
            for (int i = 0; i < 20; i++) {
                guard.handshake(ip(exempt), 20);
            }
        }
        assertThat(findings).isEmpty();
    }

    @Test
    void behindForwardingProxyAddressAndHostnameLimitsAreSkipped() {
        forwarded.set(true);
        for (int i = 0; i < 20; i++) {
            guard.handshake(ip("203.0.113.5"), 2000);
        }
        assertThat(findings).isEmpty();
    }

    @Test
    void flagsOversizedHostnameAndImpossibleNames() {
        guard.handshake(ip("203.0.113.1"), 256);
        guard.loginStart(ip("203.0.113.2"), "seventeen_letters");
        guard.loginStart(ip("203.0.113.3"), "has space");
        guard.loginStart(ip("203.0.113.4"), "");
        assertThat(ids()).containsExactly("network.hostname", "network.login-name", "network.login-name",
                "network.login-name");
    }

    @Test
    void ordinaryNamesIncludingFloodgatePrefixPass() {
        guard.loginStart(ip("203.0.113.1"), "Steve_123");
        guard.loginStart(ip("203.0.113.2"), ".BedrockPlayer");
        assertThat(findings).isEmpty();
    }

    @Test
    void trackedAddressesStayBounded() {
        for (int i = 1; i <= 100; i++) {
            guard.handshake(ip("203.0.113." + i), 20);
        }
        assertThat(guard.trackedAddresses()).isEqualTo(10);
    }

    @Test
    void disabledDoesNothing() {
        settings.set(small(NetworkSettings.Mode.DISABLED));
        for (int i = 0; i < 20; i++) {
            guard.handshake(ip("203.0.113.5"), 2000);
        }
        assertThat(findings).isEmpty();
        assertThat(guard.trackedAddresses()).isZero();
    }
}
