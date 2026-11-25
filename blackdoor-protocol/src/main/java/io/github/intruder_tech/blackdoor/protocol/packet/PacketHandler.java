package io.github.intruder_tech.blackdoor.protocol.packet;

/**
 * Receives translated packets from the protocol bridge. Called on the connection's network
 * thread: implementations must be fast, non-blocking, and must never throw (they fail open).
 */
public interface PacketHandler {

    /**
     * @param wireSize decoded packet size in bytes, or -1 if unknown
     * @return {@link PacketVerdict#DROP} to cancel the packet before the server processes it
     */
    PacketVerdict onInbound(ConnectionInfo connection, ConnectionState state, int wireSize, ServerboundPacket packet);

    void onOutbound(ConnectionInfo connection, ClientboundPacket packet);

    void onDisconnect(Object connectionKey);
}
