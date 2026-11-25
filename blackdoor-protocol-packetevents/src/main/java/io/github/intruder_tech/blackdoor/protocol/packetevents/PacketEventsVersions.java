package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;

import java.util.Optional;

/** Maps PacketEvents version enums onto {@link MinecraftVersion}. */
final class PacketEventsVersions {

    /** Sentinel constants (some deprecated upstream) referenced by name to avoid deprecation coupling. */
    static final java.util.Set<String> NON_RELEASE_CLIENT_CONSTANTS =
            java.util.Set.of("UNKNOWN", "LOWER_THAN_SUPPORTED_VERSIONS", "HIGHER_THAN_SUPPORTED_VERSIONS");

    private PacketEventsVersions() {
    }

    /**
     * Converts a client version. Returns empty for UNKNOWN and for versions outside PacketEvents'
     * table (LOWER/HIGHER_THAN_SUPPORTED_VERSIONS): callers treat empty as "unknown version", which
     * disables prediction-based checks for that player.
     */
    static Optional<MinecraftVersion> fromClient(ClientVersion version) {
        if (version == null
                || version == ClientVersion.UNKNOWN
                || NON_RELEASE_CLIENT_CONSTANTS.contains(version.name())) {
            return Optional.empty();
        }
        return MinecraftVersion.parse(version.getReleaseName());
    }

    static Optional<MinecraftVersion> fromServer(ServerVersion version) {
        if (version == null || version == ServerVersion.ERROR) {
            return Optional.empty();
        }
        return MinecraftVersion.parse(version.getReleaseName());
    }
}
