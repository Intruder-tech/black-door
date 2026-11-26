package io.github.intruder_tech.blackdoor.network;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnownAddressesTest {

    private final ManualTimeSource time = new ManualTimeSource();
    private final KnownAddresses known = new KnownAddresses(time);
    private final InetAddress a = ip("203.0.113.5");
    private final UUID one = UUID.randomUUID();
    private final UUID two = UUID.randomUUID();

    private static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void anAddressBecomesKnownAfterSixtySeconds() {
        known.joined(one, a);
        time.advance(Duration.ofSeconds(59));
        assertThat(known.isKnown(a)).isFalse();
        time.advance(Duration.ofSeconds(1));
        assertThat(known.isKnown(a)).as("still online").isTrue();
        known.left(one, 10);
        assertThat(known.isKnown(a)).as("remembered").isTrue();
        assertThat(known.size()).isEqualTo(1);
    }

    @Test
    void aShortStayIsNotRemembered() {
        known.joined(one, a);
        time.advance(Duration.ofSeconds(30));
        known.left(one, 10);
        assertThat(known.isKnown(a)).isFalse();
        assertThat(known.size()).isZero();
    }

    @Test
    void anotherPlayerLeavingEarlyDoesNotForgetTheAddress() {
        known.joined(one, a);
        time.advance(Duration.ofSeconds(60));
        known.joined(two, a);
        known.left(two, 10);
        assertThat(known.isKnown(a)).isTrue();
    }

    @Test
    void aLeaveWithoutAJoinChangesNothing() {
        known.left(one, 10);
        assertThat(known.size()).isZero();
    }

    @Test
    void theOldestAddressIsForgottenWhenFull() {
        for (int i = 1; i <= 3; i++) {
            UUID id = UUID.randomUUID();
            known.joined(id, ip("203.0.113." + i));
            time.advance(Duration.ofSeconds(60));
            known.left(id, 2);
        }
        assertThat(known.size()).isEqualTo(2);
        assertThat(known.isKnown(ip("203.0.113.1"))).isFalse();
    }
}
