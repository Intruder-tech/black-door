package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTabComplete;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTeleportConfirm;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;

/** Translates PacketEvents events into Black Door's library-independent packet model. */
final class PacketTranslator {

    private PacketTranslator() {
    }

    static ConnectionState state(com.github.retrooper.packetevents.protocol.ConnectionState state) {
        if (state == null) {
            return ConnectionState.UNKNOWN;
        }
        return switch (state.name()) {
            case "HANDSHAKING" -> ConnectionState.HANDSHAKING;
            case "STATUS" -> ConnectionState.STATUS;
            case "LOGIN" -> ConnectionState.LOGIN;
            case "CONFIGURATION" -> ConnectionState.CONFIGURATION;
            case "PLAY" -> ConnectionState.PLAY;
            default -> ConnectionState.UNKNOWN;
        };
    }

    /** Only builds wrappers for packets Black Door inspects; everything else becomes {@code Other}. */
    static ServerboundPacket inbound(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Client.PLAYER_FLYING || type == PacketType.Play.Client.PLAYER_POSITION
                || type == PacketType.Play.Client.PLAYER_ROTATION || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            WrapperPlayClientPlayerFlying w = new WrapperPlayClientPlayerFlying(event);
            Location l = w.getLocation();
            return new ServerboundPacket.Movement(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(),
                    w.hasPositionChanged(), w.hasRotationChanged(), w.isOnGround());
        }
        if (type == PacketType.Play.Client.TELEPORT_CONFIRM) {
            return new ServerboundPacket.TeleportConfirm(new WrapperPlayClientTeleportConfirm(event).getTeleportId());
        }
        if (type == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            return new ServerboundPacket.HeldItemChange(new WrapperPlayClientHeldItemChange(event).getSlot());
        }
        if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
            return new ServerboundPacket.InteractEntity(w.getEntityId(), action(w.getAction()));
        }
        if (type == PacketType.Play.Client.ATTACK) {
            return new ServerboundPacket.InteractEntity(new WrapperPlayClientAttack(event).getEntityId(),
                    ServerboundPacket.InteractEntity.Action.ATTACK);
        }
        if (type == PacketType.Play.Client.PONG) {
            return new ServerboundPacket.TransactionResponse(
                    new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong(event).getId(), false);
        }
        if (type == PacketType.Play.Client.WINDOW_CONFIRMATION) {
            var w = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation(event);
            if (w.getWindowId() == 0) {
                return new ServerboundPacket.TransactionResponse(w.getActionId(), true);
            }
            return new ServerboundPacket.Other("window_confirmation");
        }
        if (type == PacketType.Play.Client.ANIMATION) {
            return new ServerboundPacket.ArmSwing();
        }
        if (type == PacketType.Play.Client.CHAT_MESSAGE) {
            return text(ServerboundPacket.Text.Kind.CHAT, new WrapperPlayClientChatMessage(event).getMessage());
        }
        if (type == PacketType.Play.Client.CHAT_COMMAND) {
            return text(ServerboundPacket.Text.Kind.COMMAND, new WrapperPlayClientChatCommand(event).getCommand());
        }
        if (type == PacketType.Play.Client.TAB_COMPLETE) {
            return text(ServerboundPacket.Text.Kind.TAB_COMPLETE, new WrapperPlayClientTabComplete(event).getText());
        }
        if (type == PacketType.Play.Client.PLUGIN_MESSAGE) {
            var w = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage(event);
            String channel = w.getChannelName();
            byte[] data = w.getData();
            return new ServerboundPacket.PluginMessage(channel == null ? "" : channel, data == null ? 0 : data.length);
        }
        if (type == PacketType.Play.Client.EDIT_BOOK) {
            var w = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEditBook(event);
            java.util.List<String> pages = w.getPages();
            String title = w.getTitle();
            return new ServerboundPacket.BookEdit(pages == null ? 0 : pages.size(),
                    pages == null ? 0 : longest(pages.toArray(String[]::new)), title == null ? 0 : title.length());
        }
        if (type == PacketType.Play.Client.UPDATE_SIGN) {
            String[] lines = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign(event)
                    .getTextLines();
            return new ServerboundPacket.SignUpdate(lines == null ? 0 : lines.length, lines == null ? 0 : longest(lines));
        }
        if (type == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
            var w = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction(event);
            var item = w.getItemStack();
            return new ServerboundPacket.CreativeItem(w.getSlot(), item == null || item.isEmpty() ? 0 : item.getAmount());
        }
        return new ServerboundPacket.Other(type == null ? "unknown" : type.getName());
    }

    private static int longest(String[] values) {
        int max = 0;
        for (String v : values) {
            if (v != null) {
                max = Math.max(max, v.length());
            }
        }
        return max;
    }

    private static ServerboundPacket.Text text(ServerboundPacket.Text.Kind kind, String value) {
        return new ServerboundPacket.Text(kind, value == null ? 0 : value.length());
    }

    private static ServerboundPacket.InteractEntity.Action action(WrapperPlayClientInteractEntity.InteractAction action) {
        if (action == null) {
            return ServerboundPacket.InteractEntity.Action.UNKNOWN;
        }
        return switch (action) {
            case ATTACK -> ServerboundPacket.InteractEntity.Action.ATTACK;
            case INTERACT -> ServerboundPacket.InteractEntity.Action.INTERACT;
            case INTERACT_AT -> ServerboundPacket.InteractEntity.Action.INTERACT_AT;
            default -> ServerboundPacket.InteractEntity.Action.UNKNOWN;
        };
    }
}
