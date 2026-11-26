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

    /**
     * Reply to a server ping (1.17+ Pong) or window confirmation (older). {@code id} is the ping id
     * or the window-confirmation action number.
     */
    record TransactionResponse(int id, boolean legacy) implements ServerboundPacket {
    }

    /** Custom payload ("plugin message") in the play state. {@code length} is the data size in bytes. */
    record PluginMessage(String channel, int length) implements ServerboundPacket {
    }

    /** Book edit or signing (1.13+). Title length is 0 when the book is not being signed. */
    record BookEdit(int pages, int longestPage, int titleLength) implements ServerboundPacket {
    }

    /** Sign text submitted by the client. */
    record SignUpdate(int lines, int longestLine) implements ServerboundPacket {
    }

    /**
     * Creative-mode inventory action. {@code amount} is 0 for an empty stack; {@code depth} and
     * {@code elements} describe how the item's data nests (see {@link StructureSize}).
     */
    record CreativeItem(int slot, int amount, int depth, int elements) implements ServerboundPacket {
        public CreativeItem(int slot, int amount) {
            this(slot, amount, 0, 0);
        }
    }

    /** An answer the server is waiting for (keep-alive and the like). Flood mitigation never drops it. */
    record Reply(String typeName) implements ServerboundPacket {
    }

    /**
     * A packet of an inspected type that could not be decoded, typically because it exceeds the
     * protocol's own limits.
     */
    record Undecodable(Kind kind) implements ServerboundPacket {
        public enum Kind { SIGN, BOOK, PAYLOAD, CHAT, COMMAND, TAB_COMPLETE, CREATIVE_ITEM }
    }

    /** Any other packet; only counted for rate and size. */
    record Other(String typeName) implements ServerboundPacket {
    }
}
