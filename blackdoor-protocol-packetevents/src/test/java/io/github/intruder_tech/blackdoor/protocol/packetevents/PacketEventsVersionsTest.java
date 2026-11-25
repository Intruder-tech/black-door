package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.VersionSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards version onboarding: every concrete version in the pinned PacketEvents release must map to
 * a {@link MinecraftVersion}, and the newest one must be the release in {@link VersionSupport#LATEST_KNOWN}.
 */
class PacketEventsVersionsTest {


    @Test
    void everyClientVersionMaps() {
        for (ClientVersion v : ClientVersion.values()) {
            if (PacketEventsVersions.NON_RELEASE_CLIENT_CONSTANTS.contains(v.name())) {
                assertThat(PacketEventsVersions.fromClient(v)).as(v.name()).isEmpty();
            } else {
                assertThat(PacketEventsVersions.fromClient(v)).as(v.name() + " / " + v.getReleaseName()).isPresent();
            }
        }
    }

    @Test
    void everyServerVersionMaps() {
        for (ServerVersion v : ServerVersion.values()) {
            if (v == ServerVersion.ERROR) {
                assertThat(PacketEventsVersions.fromServer(v)).isEmpty();
            } else {
                assertThat(PacketEventsVersions.fromServer(v)).as(v.name() + " / " + v.getReleaseName()).isPresent();
            }
        }
    }

    @Test
    void latestKnownMatchesPinnedLibrary() {
        MinecraftVersion newest = Arrays.stream(ClientVersion.values())
                .filter(v -> !PacketEventsVersions.NON_RELEASE_CLIENT_CONSTANTS.contains(v.name()))
                .map(PacketEventsVersions::fromClient)
                .flatMap(Optional::stream)
                .max(MinecraftVersion::compareTo)
                .orElseThrow();
        assertThat(VersionSupport.classify(newest)).isEqualTo(VersionSupport.Status.KNOWN);
        assertThat(newest.major()).isEqualTo(VersionSupport.LATEST_KNOWN.major());
        assertThat(newest.minor()).isEqualTo(VersionSupport.LATEST_KNOWN.minor());
    }

    @Test
    void mapsRepresentativeVersions() {
        assertThat(PacketEventsVersions.fromClient(ClientVersion.V_1_8)).contains(MinecraftVersion.of(1, 8));
        assertThat(PacketEventsVersions.fromClient(ClientVersion.V_26_1)).contains(MinecraftVersion.of(26, 1));
    }
}
