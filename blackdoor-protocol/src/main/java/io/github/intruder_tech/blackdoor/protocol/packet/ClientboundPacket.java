package io.github.intruder_tech.blackdoor.protocol.packet;

/** Server-to-client packets Black Door observes to keep its model of the client in sync. */
public sealed interface ClientboundPacket {

    /** Login into the world; carries the player's own entity id. */
    record JoinGame(int entityId) implements ClientboundPacket {
    }

    /** The server told the client its game mode (login, respawn or a change). */
    record GameModeChange(boolean creative) implements ClientboundPacket {
    }

    /**
     * Server teleport ("synchronize player position") with the id the client must confirm.
     * {@code relative} bits mark values that are offsets: 1 x, 2 y, 4 z, 8 yaw, 16 pitch,
     * 32 to 128 velocity x to z, 256 rotate velocity.
     */
    record SyncPosition(int teleportId, double x, double y, double z, float yaw, float pitch, int relative)
            implements ClientboundPacket {
        public SyncPosition(int teleportId) {
            this(teleportId, 0, 0, 0, 0, 0, 0);
        }
    }

    /** A potion effect given to an entity. Only translated while a trace is wanted. */
    record Effect(int entityId, String effect, int amplifier, int durationTicks) implements ClientboundPacket {
    }

    /** A potion effect removed from an entity. Only translated while a trace is wanted. */
    record EffectRemoved(int entityId, String effect) implements ClientboundPacket {
    }

    /** One attribute of an entity. Only translated while a trace is wanted. */
    record Attribute(int entityId, String key, double base, java.util.List<Modifier> modifiers)
            implements ClientboundPacket {
        public record Modifier(double amount, int operation) {
        }

        public Attribute {
            modifiers = java.util.List.copyOf(modifiers);
        }
    }

    /** Flight abilities the server grants. Only translated while a trace is wanted. */
    record Abilities(boolean flying, boolean flightAllowed, float flySpeed) implements ClientboundPacket {
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
