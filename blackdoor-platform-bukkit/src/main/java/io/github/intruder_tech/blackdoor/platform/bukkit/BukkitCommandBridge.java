package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.core.command.CommandRouter;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** Connects the Bukkit command system to the platform-neutral {@link CommandRouter}. */
final class BukkitCommandBridge implements CommandExecutor, TabCompleter {

    private final CommandRouter router;
    private final PlatformScheduler scheduler;

    BukkitCommandBridge(CommandRouter router, PlatformScheduler scheduler) {
        this.router = router;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        router.dispatch(new BukkitSender(sender, scheduler), args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return router.complete(new BukkitSender(sender, scheduler), args);
    }
}
