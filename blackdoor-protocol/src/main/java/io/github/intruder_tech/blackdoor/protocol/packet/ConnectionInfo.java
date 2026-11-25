package io.github.intruder_tech.blackdoor.protocol.packet;

import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of one network connection as known at the time of a packet.
 *
 * @param key           opaque per-connection key with identity semantics (the network channel)
 * @param uniqueId      player UUID, or null before login
 * @param name          player name, or null before login
 * @param clientVersion client protocol version, or null if unknown
 */
public record ConnectionInfo(Object key, UUID uniqueId, String name, MinecraftVersion clientVersion) {

    public ConnectionInfo {
        Objects.requireNonNull(key, "key");
    }
}
