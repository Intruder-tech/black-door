package io.github.intruder_tech.blackdoor.network;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.violations.Finding;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** "Only my proxy may connect": direct connections that skip the proxy. */
class ProxyAllowlistTest {

    private final List<Finding> findings = new ArrayList<>();
    private final AtomicBoolean enforce = new AtomicBoolean(true);
    private final AtomicReference<NetworkSettings> settings =
            new AtomicReference<>(with(NetworkSettings.Mode.MITIGATE, Set.of("203.0.113.1")));
    // forwarding on: per-address limits are off, the allowlist must still work
    private final ConnectionGuard guard = new ConnectionGuard(findings::add, new ManualTimeSource(), settings::get,
            enforce::get, () -> true);

    private static NetworkSettings with(NetworkSettings.Mode mode, Set<String> proxies) {
        NetworkSettings d = NetworkSettings.defaults();
        return new NetworkSettings(mode, d.connections(), d.pings(), d.logins(), d.maxHostnameLength(),
                d.maxTrackedAddresses(), d.exemptAddresses(), d.exemptPrivate(), proxies);
    }

    private static InetAddress ip(String literal) throws Exception {
        return InetAddress.getByName(literal);
    }

    @Test
    void onlyListedProxiesMayConnect() throws Exception {
        assertThat(guard.handshake(ip("203.0.113.1"), 20)).isFalse();
        assertThat(guard.handshake(ip("198.51.100.7"), 20)).isTrue();
        assertThat(findings).extracting(Finding::checkId).containsExactly("network.direct-join");
    }

    @Test
    void watchModeOnlyRecords() throws Exception {
        settings.set(with(NetworkSettings.Mode.MONITOR, Set.of("203.0.113.1")));
        assertThat(guard.handshake(ip("198.51.100.7"), 20)).isFalse();
        assertThat(findings).hasSize(1);
    }

    @Test
    void emptyListMeansNoRestriction() throws Exception {
        settings.set(with(NetworkSettings.Mode.MITIGATE, Set.of()));
        assertThat(guard.handshake(ip("198.51.100.7"), 20)).isFalse();
        assertThat(findings).isEmpty();
    }
}
