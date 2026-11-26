package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetSocketAddress;

/** Tells the engine who is in game, so their addresses count as known during a login flood. */
public final class PresenceListener implements Listener {

    private final BlackDoorEngine engine;

    PresenceListener(BlackDoorEngine engine) {
        this.engine = engine;
    }

    /** Turns away players under a temporary ban. */
    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
        engine.banMessage(event.getUniqueId()).ifPresent(message -> event.disallow(
                org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_BANNED, message));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        InetSocketAddress socket = player.getAddress();
        if (socket != null && socket.getAddress() != null) {
            engine.playerJoined(player.getUniqueId(), socket.getAddress());
            // Another plugin may have kicked them during the join; their quit then ran first.
            if (!player.isOnline()) {
                engine.playerLeft(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        engine.playerLeft(event.getPlayer().getUniqueId());
    }
}
