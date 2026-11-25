package io.github.intruder_tech.blackdoor.protocol.packet;

/**
 * Black Door's own, library-independent view of the client-to-server packets it inspects.
 * Only fields needed by security logic are carried; large payloads are reduced to sizes.
 */
public sealed interface ServerboundPacket {

    /** Position/rotation/ground update (flying, position, rotation, position+rotation). */
    record Movement(double x, double y, double z, float yaw, float pitch,
                    boolean hasPosition, boolean hasRotation, boolean onGround) implements ServerboundPacket {
    }

    /** Client confirming a server teleport (1.9+). */
    record TeleportConfirm(int teleportId) implements ServerboundPacket {
    }

    /** Hotbar slot selection. */
    record HeldItemChange(int slot) implements ServerboundPacket {
    }

    /** Attack or interaction with an entity. */
    record InteractEntity(int entityId, Action action) implements ServerboundPacket {
        public enum Action { INTERACT, ATTACK, INTERACT_AT, UNKNOWN }
    }

    /** Arm swing animation. */
    record ArmSwing() implements ServerboundPacket {
    }

    /** Free text the client sends: chat, commands, tab completion. Only the length is kept. */
    record Text(Kind kind, int length) implements ServerboundPacket {
        public enum Kind { CHAT, COMMAND, TAB_COMPLETE }
    }

    /** Any other packet; only counted for rate and size. */
    record Other(String typeName) implements ServerboundPacket {
    }
}
