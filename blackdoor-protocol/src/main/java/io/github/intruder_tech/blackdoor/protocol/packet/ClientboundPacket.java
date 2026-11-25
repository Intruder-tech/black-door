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

    /**
     * The client entered a world (login or respawn/dimension change). {@code worldName} is null on
     * versions that do not send one; chunk data is then kept private to the player.
     */
    record WorldChange(String worldName) implements ClientboundPacket {
    }

    /** A chunk sent to the client. The block view is valid only during the callback. */
    record ChunkData(int chunkX, int chunkZ, int minSectionY,
                     io.github.intruder_tech.blackdoor.protocol.world.BlockSource blocks) implements ClientboundPacket {
    }

    /** The client forgets a chunk. */
    record ChunkUnload(int chunkX, int chunkZ) implements ClientboundPacket {
    }

    /** One block changed (multi-block changes arrive as several of these). */
    record BlockChange(int x, int y, int z, int blockStateId) implements ClientboundPacket {
    }

    /** Entities removed from the client's view. */
    record EntityRemove(int[] entityIds) implements ClientboundPacket {
        public EntityRemove {
            entityIds = entityIds.clone();
        }
    }
}
