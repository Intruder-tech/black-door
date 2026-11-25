package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Single-main-thread scheduler for Spigot, Bukkit and Paper before 1.20. */
final class BukkitPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    BukkitPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void runForPlayer(UUID playerId, Runnable task) {
        runGlobal(() -> {
            if (Bukkit.getPlayer(playerId) != null) {
                task.run();
            }
        });
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
