package io.github.intruder_tech.blackdoor.protocol.packet;

/** Server-to-client packets Black Door observes to keep its model of the client in sync. */
public sealed interface ClientboundPacket {

    /** Login into the world; carries the player's own entity id. */
    record JoinGame(int entityId) implements ClientboundPacket {
    }

    /** Server teleport ("synchronize player position") with the id the client must confirm. */
    record SyncPosition(int teleportId) implements ClientboundPacket {
    }

    /** Velocity set by the server (knockback, explosions, plugins) for an entity. */
    record Velocity(int entityId, double x, double y, double z) implements ClientboundPacket {
    }

    /** An entity becomes visible to the client at an absolute position. */
    record EntitySpawn(int entityId, double x, double y, double z) implements ClientboundPacket {
    }

    /** Relative entity movement. */
    record EntityMove(int entityId, double dx, double dy, double dz) implements ClientboundPacket {
    }

    /** Absolute entity position (teleport or position sync). */
    record EntityTeleport(int entityId, double x, double y, double z) implements ClientboundPacket {
    }

    /** Entities removed from the client's view. */
    record EntityRemove(int[] entityIds) implements ClientboundPacket {
        public EntityRemove {
            entityIds = entityIds.clone();
        }
    }
}
