package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.api.BlackDoor;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.platform.PlatformInfo;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;
import io.github.intruder_tech.blackdoor.protocol.VersionSupport;

import java.util.List;
import java.util.Locale;

public final class VersionCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public VersionCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "version";
    }

    @Override
    public String permission() {
        return "blackdoor.command.version";
    }

    @Override
    public String description() {
        return "Plugin, server and Java versions";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        String sep = " &8" + Text.BULLET + " ";
        PlatformInfo info = engine.platform().info();
        String type = info.type().name();
        sender.sendMessage(Text.header("Version"));
        sender.sendMessage(Text.row("Black Door", "&a" + engine.platform().pluginVersion() + " &8(API " + BlackDoor.API_VERSION + ")"));
        sender.sendMessage(Text.row("Server", "&b" + type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT)
                + " &8" + Text.escape(info.implementationVersion())));
        info.minecraft().ifPresent(v -> sender.sendMessage(Text.row("Minecraft", "&b" + v + sep
                + (VersionSupport.classify(v) == VersionSupport.Status.KNOWN
                ? "&a" + Text.CHECK + " supported" : "&e! not fully supported"))));
        ProtocolBridge protocol = engine.platform().protocol();
        if (!info.type().isProxy()) {
            sender.sendMessage(Text.row("Packet library", protocol.isAvailable()
                    ? "&f" + protocol.name() + " &8" + Text.escape(protocol.libraryVersion())
                    : "&c" + Text.CROSS + " not installed"));
        }
        sender.sendMessage(Text.row("Java", "&f" + Runtime.version().feature()));
        sender.sendMessage(Text.footer());
    }
}
