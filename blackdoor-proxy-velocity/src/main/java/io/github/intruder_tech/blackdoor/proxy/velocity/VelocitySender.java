package io.github.intruder_tech.blackdoor.proxy.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Text;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;
import java.util.UUID;

record VelocitySender(CommandSource source) implements BdSender {

    @Override
    public String name() {
        return source instanceof Player player ? player.getUsername() : "console";
    }

    @Override
    public boolean isConsole() {
        return source instanceof ConsoleCommandSource;
    }

    @Override
    public boolean hasPermission(String node) {
        return source.hasPermission(node);
    }

    @Override
    public void sendMessage(String message) {
        source.sendMessage(LegacyComponentSerializer.legacySection().deserialize(Text.toLegacy(message)));
    }

    @Override
    public Optional<UUID> uniqueId() {
        return source instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }
}
