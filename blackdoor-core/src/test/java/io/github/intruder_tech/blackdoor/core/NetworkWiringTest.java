package io.github.intruder_tech.blackdoor.core;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketVerdict;
import io.github.intruder_tech.blackdoor.protocol.packet.PreLogin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The handler attached to the packet layer routes pre-login traffic to the connection guard. */
class NetworkWiringTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;

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
    void pingFloodFromOneAddressBecomesAFindingAndPassesInMonitorMode() throws Exception {
        PacketHandler handler = engine.packetHandler().orElseThrow();
        InetAddress address = InetAddress.getByName("203.0.113.5");
        for (int i = 0; i < 100; i++) {
            assertThat(handler.onPreLogin(address, new PreLogin.StatusRequest())).isEqualTo(PacketVerdict.PASS);
        }
        assertThat(engine.ledger().orElseThrow().countsByCheck()).containsKey("network.ping-rate");
    }

    @Test
    void writesNetworkConfigWithMonitorDefault() throws Exception {
        assertThat(Files.readString(dir.resolve("network.yml"))).contains("mode: monitor");
        assertThat(engine.networkSettings().mode().name()).isEqualTo("MONITOR");
    }
}
