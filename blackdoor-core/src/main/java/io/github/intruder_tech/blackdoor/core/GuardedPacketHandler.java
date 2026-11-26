package io.github.intruder_tech.blackdoor.core;

import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.network.ConnectionGuard;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketVerdict;
import io.github.intruder_tech.blackdoor.protocol.packet.PreLogin;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;

import java.net.InetAddress;

/**
 * Joins the two consumers of the packet layer: in-game packets go to the packet engine, pre-login
 * packets to the connection guard. Pre-login errors fail open, like the packet engine.
 */
final class GuardedPacketHandler implements PacketHandler {

    private final PacketHandler inGame;
    private final ConnectionGuard guard;
    private final RateLimitedLogger errors;

    GuardedPacketHandler(PacketHandler inGame, ConnectionGuard guard, RateLimitedLogger errors) {
        this.inGame = inGame;
        this.guard = guard;
        this.errors = errors;
    }

    @Override
    public PacketVerdict onInbound(ConnectionInfo connection, ConnectionState state, int wireSize, ServerboundPacket packet) {
        return inGame.onInbound(connection, state, wireSize, packet);
    }

    @Override
    public void onOutbound(ConnectionInfo connection, ClientboundPacket packet) {
        inGame.onOutbound(connection, packet);
    }

    @Override
    public boolean wantsMovementDetail() {
        return inGame.wantsMovementDetail();
    }

    @Override
    public void onDisconnect(Object connectionKey) {
        inGame.onDisconnect(connectionKey);
    }

    @Override
    public PacketVerdict onPreLogin(InetAddress address, PreLogin packet) {
        try {
            boolean refuse = false;
            if (packet instanceof PreLogin.Handshake h) {
                refuse = guard.handshake(address, h.hostnameLength());
            } else if (packet instanceof PreLogin.StatusRequest) {
                refuse = guard.statusPing(address);
            } else if (packet instanceof PreLogin.LoginStart l) {
                refuse = guard.loginStart(address, l.name());
            }
            return refuse ? PacketVerdict.DROP : PacketVerdict.PASS;
        } catch (RuntimeException e) {
            errors.error("connection-guard", "Connection guard error (connection allowed)", e);
            return PacketVerdict.PASS;
        }
    }
}
