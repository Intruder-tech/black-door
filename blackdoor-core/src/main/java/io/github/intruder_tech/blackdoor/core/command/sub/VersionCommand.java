package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.api.BlackDoor;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.platform.PlatformInfo;

import java.util.List;

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
        return "Shows Black Door, platform and Java versions.";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        PlatformInfo info = engine.platform().info();
        sender.sendMessage(Text.PREFIX + "&fBlack Door &a" + engine.platform().pluginVersion()
                + " &7(API " + BlackDoor.API_VERSION + ")");
        sender.sendMessage("&8 - &7Platform: &f" + info.type() + " &7(" + Text.escape(info.implementationName())
                + " " + Text.escape(info.implementationVersion()) + ")");
        info.minecraft().ifPresent(v -> sender.sendMessage("&8 - &7Minecraft: &f" + v));
        sender.sendMessage("&8 - &7Java: &f" + Runtime.version().feature());
    }
}
