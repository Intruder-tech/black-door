package io.github.intruder_tech.blackdoor.proxy.bungee;

import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Text;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Optional;
import java.util.UUID;

record BungeeSender(CommandSender sender) implements BdSender {

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public boolean isConsole() {
        return !(sender instanceof ProxiedPlayer);
    }

    @Override
    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(TextComponent.fromLegacy(Text.toLegacy(message)));
    }

    @Override
    public Optional<UUID> uniqueId() {
        return sender instanceof ProxiedPlayer player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }
}
