package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;

import java.util.List;

/** Admin side of the login system: remove an account whose password was forgotten. */
public final class AuthCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public AuthCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "auth";
    }

    @Override
    public String permission() {
        return "blackdoor.command.auth";
    }

    @Override
    public String description() {
        return "Reset a forgotten password";
    }

    @Override
    public String usage() {
        return "auth reset <player>";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        if (args.size() != 2 || !args.get(0).equalsIgnoreCase("reset") || engine.auth().isEmpty()) {
            sender.sendMessage(Text.PREFIX + "&cUsage: &f/" + engine.commands().label() + " " + usage());
            return;
        }
        String player = args.get(1);
        String actor = sender.isConsole() ? "console" : sender.name();
        boolean queued = engine.submitWork(() -> {
            boolean removed = engine.auth().orElseThrow().reset(player);
            engine.audit().ifPresent(a -> a.record(actor, "auth.reset " + player, removed ? "success" : "no account"));
            sender.sendMessage(Text.PREFIX + (removed
                    ? "&aAccount removed. " + Text.escape(player) + " can /signup again on the next join."
                    : "&cNo account named &f" + Text.escape(player) + "&c."));
        });
        if (!queued) {
            sender.sendMessage(Text.PREFIX + "&cBlack Door is busy; try again.");
        }
    }
}
