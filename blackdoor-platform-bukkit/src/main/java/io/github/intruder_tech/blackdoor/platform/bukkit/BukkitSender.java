package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** Wraps a Bukkit sender; messages are delivered on the thread that owns the recipient. */
final class BukkitSender implements BdSender {

    private final CommandSender sender;
    private final PlatformScheduler scheduler;

    BukkitSender(CommandSender sender, PlatformScheduler scheduler) {
        this.sender = sender;
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public boolean isConsole() {
        return !(sender instanceof Player);
    }

    @Override
    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
    }

    @Override
    public void sendMessage(String message) {
        String legacy = Text.toLegacy(message);
        if (sender instanceof Player player) {
            scheduler.runForPlayer(player.getUniqueId(), () -> player.sendMessage(legacy));
        } else {
            scheduler.runGlobal(() -> sender.sendMessage(legacy));
        }
    }

    @Override
    public Optional<UUID> uniqueId() {
        return sender instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }
}
