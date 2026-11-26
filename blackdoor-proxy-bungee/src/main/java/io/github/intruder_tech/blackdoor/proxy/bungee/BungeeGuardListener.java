package io.github.intruder_tech.blackdoor.proxy.bungee;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.ProxyGate;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.ClientConnectEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Connection and login limits. BungeeCord cannot refuse a ping event, so pings are limited only as
 * connections. Public because BungeeCord calls the handlers reflectively.
 */
public final class BungeeGuardListener implements Listener {

    private final ProxyGate gate;
    private final BlackDoorEngine engine;

    BungeeGuardListener(ProxyGate gate, BlackDoorEngine engine) {
        this.gate = gate;
        this.engine = engine;
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        InetAddress address = address(event.getPlayer().getSocketAddress());
        if (address != null) {
            engine.playerJoined(event.getPlayer().getUniqueId(), address);
            // Another plugin may already have disconnected them, before we recorded the join.
            if (!event.getPlayer().isConnected()) {
                engine.playerLeft(event.getPlayer().getUniqueId());
            }
        }
    }

    @EventHandler
    public void onLeave(PlayerDisconnectEvent event) {
        engine.playerLeft(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onConnect(ClientConnectEvent event) {
        if (!event.isCancelled() && gate.connect(address(event.getSocketAddress()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        if (!event.isCancelled() && gate.login(address(event.getConnection().getSocketAddress()),
                event.getConnection().getName())) {
            event.setReason(new TextComponent(ProxyGate.REFUSAL_MESSAGE));
            event.setCancelled(true);
        }
    }

    /** Null for non-network sockets (unix domain), which are never limited. */
    private static InetAddress address(SocketAddress socket) {
        return socket instanceof InetSocketAddress inet ? inet.getAddress() : null;
    }
}
