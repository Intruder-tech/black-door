package io.github.intruder_tech.blackdoor.state;

import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;

import java.util.UUID;

/** Immutable view of a session for commands and diagnostics (taken off the network thread). */
public record SessionSnapshot(UUID uniqueId, String name, MinecraftVersion clientVersion, int entityId,
                              long ageMillis, long inboundPackets, boolean hasPosition,
                              double x, double y, double z, float yaw, float pitch, boolean onGround,
                              int pendingTeleports, long teleportOverflow) {
}
