package io.github.intruder_tech.blackdoor.core;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.config.CoreConfig;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Join and ping limits on proxies, behind the calls the proxy adapters make. */
class ProxyGuardTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;
    private InetAddress address;

    @BeforeEach
    void setUp() throws Exception {
        engine = new BlackDoorEngine(new FakePlatform(dir, PlatformType.VELOCITY, null, false), TimeSource.system());
        engine.enable();
        address = InetAddress.getByName("203.0.113.5");
    }

    @AfterEach
    void tearDown() {
        engine.disable();
    }

    private void block() {
        assertThat(engine.applyChange(CoreConfig.NETWORK, "mode", "mitigate", "test").success()).isTrue();
        assertThat(engine.applyChange(CoreConfig.GENERAL, "mode", "enforce", "test").success()).isTrue();
    }

    private static int refused(int attempts, BooleanSupplier call) {
        int refused = 0;
        for (int i = 0; i < attempts; i++) {
            if (call.getAsBoolean()) {
                refused++;
            }
        }
        return refused;
    }

    @Test
    void watchModeRefusesNothingButRecords() {
        ProxyGate gate = engine.proxyGate().orElseThrow();
        assertThat(refused(50, () -> gate.login(address, "Steve"))).isZero();
        assertThat(engine.ledger().orElseThrow().countsByCheck()).containsKey("network.login-rate");
    }

    @Test
    void blockModeRefusesLoginFloods() {
        block();
        ProxyGate gate = engine.proxyGate().orElseThrow();
        assertThat(refused(50, () -> gate.login(address, "Steve"))).isPositive().isLessThan(50);
    }

    @Test
    void pingWithHostnameAlsoCountsAsAConnection() {
        block();
        ProxyGate gate = engine.proxyGate().orElseThrow();
        assertThat(gate.ping(address, 5000)).as("oversized hostname").isTrue();
        assertThat(refused(100, () -> gate.ping(address, 20))).isPositive();
        assertThat(engine.ledger().orElseThrow().countsByCheck())
                .containsKeys("network.hostname", "network.connection-rate");
    }

    @Test
    void connectionFloodIsRefused() {
        block();
        ProxyGate gate = engine.proxyGate().orElseThrow();
        assertThat(refused(100, () -> gate.connect(address))).isPositive();
    }

    @Test
    void unknownAddressPasses() {
        block();
        ProxyGate gate = engine.proxyGate().orElseThrow();
        assertThat(gate.connect(null)).isFalse();
        assertThat(gate.login(null, "Steve")).isFalse();
        assertThat(gate.ping(null, 5000)).isFalse();
    }

    @Test
    void backendsHaveNoProxyGate(@TempDir Path other) {
        BlackDoorEngine backend = new BlackDoorEngine(FakePlatform.paper(other), TimeSource.system());
        backend.enable();
        try {
            assertThat(backend.proxyGate()).isEmpty();
        } finally {
            backend.disable();
        }
    }
}
