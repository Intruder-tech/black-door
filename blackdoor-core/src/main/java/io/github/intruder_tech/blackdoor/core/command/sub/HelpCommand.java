package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;

import java.util.List;

public final class HelpCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public HelpCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String permission() {
        return "blackdoor.command.help";
    }

    @Override
    public String description() {
        return "This list";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        engine.commands().sendHelp(sender);
    }
}
