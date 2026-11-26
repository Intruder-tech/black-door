package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
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
                preLogin(event);
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

    /** Handshake, status ping and login start. A refused connection is closed. */
    private void preLogin(PacketReceiveEvent event) {
        var type = event.getPacketType();
        io.github.intruder_tech.blackdoor.protocol.packet.PreLogin packet;
        if (type == PacketType.Handshaking.Client.HANDSHAKE) {
            String host = new com.github.retrooper.packetevents.wrapper.handshaking.client
                    .WrapperHandshakingClientHandshake(event).getServerAddress();
            packet = new io.github.intruder_tech.blackdoor.protocol.packet.PreLogin.Handshake(host == null ? 0 : host.length());
        } else if (type == PacketType.Status.Client.REQUEST) {
            packet = new io.github.intruder_tech.blackdoor.protocol.packet.PreLogin.StatusRequest();
        } else if (type == PacketType.Login.Client.LOGIN_START) {
            packet = new io.github.intruder_tech.blackdoor.protocol.packet.PreLogin.LoginStart(
                    new com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart(event)
                            .getUsername());
        } else {
            return;
        }
        User user = event.getUser();
        if (user == null || user.getAddress() == null || user.getAddress().getAddress() == null) {
            return;
        }
        if (handler.onPreLogin(user.getAddress().getAddress(), packet) == PacketVerdict.DROP) {
            event.setCancelled(true);
            user.closeConnection();
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        try {
            if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
                ConnectionInfo info = info(event.getUser(), event.getChannel());
                if (info != null) {
                    WrapperPlayServerJoinGame join = new WrapperPlayServerJoinGame(event);
                    handler.onOutbound(info, new ClientboundPacket.JoinGame(join.getEntityId()));
                    handler.onOutbound(info, new ClientboundPacket.WorldChange(join.getWorldName()));
                    gameMode(info, join.getGameMode());
                }
            } else if (event.getPacketType() == PacketType.Play.Server.RESPAWN) {
                ConnectionInfo info = info(event.getUser(), event.getChannel());
                if (info != null) {
                    var respawn = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn(event);
                    handler.onOutbound(info, new ClientboundPacket.WorldChange(respawn.getWorldName().orElse(null)));
                    gameMode(info, respawn.getGameMode());
                }
            } else if (event.getPacketType() == PacketType.Play.Server.CHANGE_GAME_STATE) {
                ConnectionInfo info = info(event.getUser(), event.getChannel());
                var change = new WrapperPlayServerChangeGameState(event);
                if (info != null && change.getReason() == WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE) {
                    gameMode(info, GameMode.getById((int) change.getValue()));
                }
            } else if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
                ConnectionInfo info = info(event.getUser(), event.getChannel());
                var column = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData(event).getColumn();
                if (info != null && column != null && column.isFullChunk()) {
                    handler.onOutbound(info, new ClientboundPacket.ChunkData(column.getX(), column.getZ(),
                            event.getUser().getMinWorldHeight() >> 4, new ColumnBlockSource(column.getChunks())));
                }
            } else if (event.getPacketType() == PacketType.Play.Server.UNLOAD_CHUNK) {
                ConnectionInfo info = info(event.getUser(), event.getChannel());
                var unload = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk(event);
                if (info != null) {
                    handler.onOutbound(info, new ClientboundPacket.ChunkUnload(unload.getChunkX(), unload.getChunkZ()));
                }
            } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
                ConnectionInfo info = info(event.getUser(), event.getChannel());
                var change = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange(event);
                if (info != null) {
                    var p = change.getBlockPosition();
                    handler.onOutbound(info, new ClientboundPacket.BlockChange(p.getX(), p.getY(), p.getZ(), change.getBlockId()));
                }
            } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                ConnectionInfo info = info(event.getUser(), event.getChannel());
                if (info != null) {
                    for (var b : new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange(event).getBlocks()) {
                        handler.onOutbound(info, new ClientboundPacket.BlockChange(b.getX(), b.getY(), b.getZ(), b.getBlockId()));
                    }
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

    private void gameMode(ConnectionInfo info, GameMode mode) {
        if (mode != null) {
            handler.onOutbound(info, new ClientboundPacket.GameModeChange(mode == GameMode.CREATIVE));
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
