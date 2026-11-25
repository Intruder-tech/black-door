package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketVerdict;

import java.util.concurrent.atomic.LongAdder;

/**
 * PacketEvents listener that feeds {@link PacketHandler}. Translation errors never propagate:
 * the packet passes untouched (fail-open) and the error is counted.
 */
final class BlackDoorPacketListener implements PacketListener {

    private final PacketHandler handler;
    final LongAdder translationErrors = new LongAdder();

    BlackDoorPacketListener(PacketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        try {
            ConnectionState state = PacketTranslator.state(event.getConnectionState());
            if (state != ConnectionState.PLAY) {
                return;
            }
            ConnectionInfo info = info(event.getUser(), event.getChannel());
            if (info == null) {
                return;
            }
            int size = event.getByteBuf() == null ? -1 : ByteBufHelper.readableBytes(event.getByteBuf());
            if (handler.onInbound(info, state, size, PacketTranslator.inbound(event)) == PacketVerdict.DROP) {
                event.setCancelled(true);
            }
        } catch (RuntimeException e) {
            translationErrors.increment();
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        try {
            if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
                ConnectionInfo info = info(event.getUser(), event.getChannel());
                if (info != null) {
                    handler.onOutbound(info, new ClientboundPacket.JoinGame(new WrapperPlayServerJoinGame(event).getEntityId()));
                }
            } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
                ConnectionInfo info = info(event.getUser(), event.getChannel());
                if (info != null) {
                    handler.onOutbound(info, new ClientboundPacket.SyncPosition(
                            new WrapperPlayServerPlayerPositionAndLook(event).getTeleportId()));
                }
            } else {
                ClientboundPacket tracked = OutboundTranslator.translate(event);
                if (tracked != null) {
                    ConnectionInfo info = info(event.getUser(), event.getChannel());
                    if (info != null) {
                        handler.onOutbound(info, tracked);
                    }
                }
            }
        } catch (RuntimeException e) {
            translationErrors.increment();
        }
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        try {
            User user = event.getUser();
            if (user != null && user.getChannel() != null) {
                handler.onDisconnect(user.getChannel());
            }
        } catch (RuntimeException e) {
            translationErrors.increment();
        }
    }

    private static ConnectionInfo info(User user, Object channel) {
        Object key = user != null && user.getChannel() != null ? user.getChannel() : channel;
        if (key == null) {
            return null;
        }
        if (user == null) {
            return new ConnectionInfo(key, null, null, null);
        }
        String name = user.getProfile() == null ? null : user.getProfile().getName();
        return new ConnectionInfo(key, user.getUUID(), name, PacketEventsVersions.fromClient(user.getClientVersion()).orElse(null));
    }
}
