package io.github.intruder_tech.blackdoor.protocol;

import java.util.Optional;

/**
 * Black Door's view of the packet layer (ADR-003). Implementations live in adapter modules; no
 * other module may reference a packet library directly.
 *
 * <p>Phase 1 exposes availability and version detection. Packet listeners are added in Phase 2.
 */
public interface ProtocolBridge {

    /** Short name of the implementation, for diagnostics. */
    String name();

    /** Whether the underlying packet library is loaded and initialized. */
    boolean isAvailable();

    /** Version of the underlying library, for diagnostics. */
    String libraryVersion();

    /** Server version as reported by the packet library, if known. */
    Optional<MinecraftVersion> serverVersion();

    /**
     * Client protocol version of a connected player (accounts for ViaVersion and friends).
     *
     * @param platformPlayer the platform's player object (e.g. a Bukkit {@code Player})
     */
    Optional<MinecraftVersion> clientVersion(Object platformPlayer);
}
