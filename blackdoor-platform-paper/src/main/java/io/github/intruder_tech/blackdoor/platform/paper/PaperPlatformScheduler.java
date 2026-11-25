package io.github.intruder_tech.blackdoor.platform.paper;

import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Scheduler for Paper 1.20+ and Folia. Global work goes to the global region scheduler; player
 * work goes to the player's own entity scheduler, which on Folia runs on the region thread that
 * owns the player (ADR-006). Instantiated reflectively by the Bukkit entry point.
 */
public final class PaperPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    public PaperPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void runForPlayer(UUID playerId, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        // [verify] Bukkit.getPlayer(UUID) is documented as safe off the owning thread on Folia.
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            // Retired callback null: if the player leaves first, the task is simply dropped.
            player.getScheduler().execute(plugin, task, null, 1L);
        }
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        // Entity-scheduled tasks are retired automatically when the plugin is disabled.
    }
}
