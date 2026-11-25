package io.github.intruder_tech.blackdoor.state;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateTest {

    @Test
    void teleportsConfirmInOrderOutOfOrderAndUnknown() {
        PendingTeleports t = new PendingTeleports(4);
        t.sent(1);
        t.sent(2);
        t.sent(3);
        assertThat(t.confirm(1)).isEqualTo(PendingTeleports.Result.CONFIRMED_IN_ORDER);
        assertThat(t.confirm(3)).isEqualTo(PendingTeleports.Result.CONFIRMED_OUT_OF_ORDER);
        assertThat(t.size()).isZero();
        assertThat(t.confirm(2)).isEqualTo(PendingTeleports.Result.UNKNOWN);
        assertThat(t.confirm(99)).isEqualTo(PendingTeleports.Result.UNKNOWN);
    }

    @Test
    void teleportsNeverGrowBeyondCapacity() {
        PendingTeleports t = new PendingTeleports(4);
        for (int i = 0; i < 1000; i++) {
            t.sent(i);
        }
        assertThat(t.size()).isEqualTo(4);
        assertThat(t.overflowed()).isEqualTo(996);
        assertThat(t.confirm(999)).isEqualTo(PendingTeleports.Result.CONFIRMED_OUT_OF_ORDER);
        assertThat(t.confirm(0)).isEqualTo(PendingTeleports.Result.UNKNOWN);
    }

    @Test
    void historyKeepsNewestFirstAndWraps() {
        MovementHistory h = new MovementHistory(3);
        for (int i = 1; i <= 5; i++) {
            h.record(i, i * 10, i * 100, i, -i, i % 2 == 0);
        }
        assertThat(h.size()).isEqualTo(3);
        assertThat(h.x(0)).isEqualTo(5);
        assertThat(h.x(2)).isEqualTo(3);
        assertThat(h.onGround(1)).isTrue();
        assertThatThrownBy(() -> h.x(3)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void registryIsBoundedAndFindsByNameOrUuid() {
        ManualTimeSource time = new ManualTimeSource();
        SessionRegistry registry = new SessionRegistry(2, time);
        UUID id = UUID.randomUUID();
        ConnectionInfo steve = new ConnectionInfo(new Object(), id, "Steve", MinecraftVersion.of(1, 21, 4));
        assertThat(registry.session(steve)).isPresent();
        assertThat(registry.session(new ConnectionInfo(new Object(), null, null, null))).isPresent();
        assertThat(registry.session(new ConnectionInfo(new Object(), null, null, null))).isEmpty();
        assertThat(registry.rejected()).isEqualTo(1);
        assertThat(registry.find("steve")).isPresent();
        assertThat(registry.find(id.toString()).orElseThrow().clientVersion()).isEqualTo(MinecraftVersion.of(1, 21, 4));
        registry.remove(steve.key());
        assertThat(registry.find("Steve")).isEmpty();
    }

    @Test
    void attachmentsAreCreatedOnce() {
        AttachmentKey<StringBuilder> key = AttachmentKey.create("test");
        SessionRegistry registry = new SessionRegistry(4, new ManualTimeSource());
        PlayerSession s = registry.session(new ConnectionInfo(new Object(), null, null, null)).orElseThrow();
        StringBuilder first = s.attachment(key, StringBuilder::new);
        assertThat(s.attachment(key, StringBuilder::new)).isSameAs(first);
    }
}
