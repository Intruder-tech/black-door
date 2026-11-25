package io.github.intruder_tech.blackdoor.protocol;

import org.junit.jupiter.api.Test;

import static io.github.intruder_tech.blackdoor.protocol.MinecraftVersion.of;
import static org.assertj.core.api.Assertions.assertThat;

class ProtocolFeatureTest {

    @Test
    void featureBoundariesAreInclusiveExclusive() {
        assertThat(ProtocolFeature.IDLE_FLYING_PACKET.presentIn(of(1, 8, 8))).isTrue();
        assertThat(ProtocolFeature.IDLE_FLYING_PACKET.presentIn(of(1, 9))).isFalse();
        assertThat(ProtocolFeature.CONFIGURATION_STATE.presentIn(of(1, 20, 1))).isFalse();
        assertThat(ProtocolFeature.CONFIGURATION_STATE.presentIn(of(1, 20, 2))).isTrue();
        assertThat(ProtocolFeature.ITEM_COMPONENTS.presentIn(of(26, 1))).isTrue();
    }

    @Test
    void clientFeaturesFollowClientVersionUnderTranslation() {
        // A 1.8 client on a 26.x server through ViaRewind.
        PlayerVersions versions = new PlayerVersions(of(1, 8), of(26, 3));
        assertThat(versions.translated()).isTrue();
        assertThat(versions.has(ProtocolFeature.IDLE_FLYING_PACKET)).isTrue();
        assertThat(versions.has(ProtocolFeature.PING_PONG_TRANSACTIONS)).isFalse();
        assertThat(versions.has(ProtocolFeature.ITEM_COMPONENTS)).isTrue();
        assertThat(versions.has(ProtocolFeature.ATTACK_COOLDOWN)).isTrue();
    }

    @Test
    void unverifiedFeaturesAreFlagged() {
        assertThat(ProtocolFeature.DIRECTIONAL_INPUT_PACKET.verified()).isFalse();
    }

    @Test
    void versionSupportClassification() {
        assertThat(VersionSupport.classify(of(1, 7, 10))).isEqualTo(VersionSupport.Status.TOO_OLD);
        assertThat(VersionSupport.classify(of(1, 8, 8))).isEqualTo(VersionSupport.Status.KNOWN);
        assertThat(VersionSupport.classify(of(26, 3))).isEqualTo(VersionSupport.Status.KNOWN);
        assertThat(VersionSupport.classify(of(26, 3, 1))).isEqualTo(VersionSupport.Status.KNOWN);
        assertThat(VersionSupport.classify(of(26, 4))).isEqualTo(VersionSupport.Status.NEWER_THAN_KNOWN);
        assertThat(VersionSupport.classify(of(27, 1))).isEqualTo(VersionSupport.Status.NEWER_THAN_KNOWN);
    }
}
