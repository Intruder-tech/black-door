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

    /**
     * Starts delivering packets to {@code handler}. At most one handler is attached at a time.
     *
     * @return false if the packet library is unavailable (nothing is attached)
     */
    default boolean attach(io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler handler) {
        return false;
    }

    /** Stops packet delivery. Safe to call when nothing is attached. */
    default void detach() {
    }

    /**
     * True when transactions must use window-confirmation action numbers (short ids, servers
     * before 1.17) instead of Ping/Pong (int ids).
     */
    default boolean usesLegacyTransactions() {
        return false;
    }

    /**
     * Sends a transaction (Ping, or window confirmation on legacy servers) with the given id to the
     * connection, without passing it through other plugins' packet listeners.
     *
     * @return false if it could not be sent
     */
    default boolean sendTransaction(Object connectionKey, int id) {
        return false;
    }
}
