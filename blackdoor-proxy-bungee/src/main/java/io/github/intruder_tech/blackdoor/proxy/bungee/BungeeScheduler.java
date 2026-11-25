package io.github.intruder_tech.blackdoor.proxy.bungee;

import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.UUID;

/** BungeeCord APIs used here are thread-safe; tasks run on the proxy's scheduler pool. */
record BungeeScheduler(Plugin plugin) implements PlatformScheduler {

    @Override
    public void runGlobal(Runnable task) {
        ProxyServer.getInstance().getScheduler().runAsync(plugin, task);
    }

    @Override
    public void runForPlayer(UUID playerId, Runnable task) {
        if (ProxyServer.getInstance().getPlayer(playerId) != null) {
            runGlobal(task);
        }
    }

    @Override
    public void cancelAll() {
        ProxyServer.getInstance().getScheduler().cancel(plugin);
    }
}
