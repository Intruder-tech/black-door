package io.github.intruder_tech.blackdoor.protocol.packet;

/** Server-to-client packets Black Door observes to keep its model of the client in sync. */
public sealed interface ClientboundPacket {

    /** Login into the world; carries the player's own entity id. */
    record JoinGame(int entityId) implements ClientboundPacket {
    }

    /** Server teleport ("synchronize player position") with the id the client must confirm. */
    record SyncPosition(int teleportId) implements ClientboundPacket {
    }
}
