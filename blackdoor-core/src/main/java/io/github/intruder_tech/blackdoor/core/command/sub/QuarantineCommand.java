package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.enforcement.BanBook;
import io.github.intruder_tech.blackdoor.enforcement.QuarantineBook;
import io.github.intruder_tech.blackdoor.state.SessionSnapshot;

import java.util.List;

/** Staff control of quarantine: list, put an online player in, release. */
public final class QuarantineCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public QuarantineCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "quarantine";
    }

    @Override
    public String permission() {
        return "blackdoor.command.quarantine";
    }

    @Override
    public String description() {
        return "Hold or release a suspect";
    }

    @Override
    public String usage() {
        return "quarantine [<player> | release <player>]";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        QuarantineBook book = engine.quarantine().orElse(null);
        String actor = sender.isConsole() ? "console" : sender.name();
        if (book == null) {
            sender.sendMessage(Text.PREFIX + "&cQuarantine is only available on game servers.");
        } else if (args.isEmpty()) {
            List<QuarantineBook.Held> held = book.active();
            sender.sendMessage(Text.PREFIX + (held.isEmpty() ? "&7Nobody is in quarantine."
                    : "&fIn quarantine: " + held.size()));
            long now = engine.time().currentTimeMillis();
            held.stream().limit(20).forEach(h -> sender.sendMessage(" &f" + Text.escape(h.name()) + " &8- &7"
                    + (h.untilMillis() == 0 ? "until released" : BanBook.words(h.untilMillis() - now) + " left")));
        } else if (args.size() == 2 && args.get(0).equalsIgnoreCase("release")) {
            sender.sendMessage(Text.PREFIX + (engine.release(args.get(1), actor)
                    ? "&aReleased " + Text.escape(args.get(1)) + "."
                    : "&c" + Text.escape(args.get(1)) + " is not in quarantine."));
        } else if (args.size() == 1) {
            SessionSnapshot online = engine.sessions().flatMap(s -> s.find(args.get(0))).orElse(null);
            if (online == null || online.uniqueId() == null) {
                sender.sendMessage(Text.PREFIX + "&cNo online player named &f" + Text.escape(args.get(0)) + "&c.");
            } else {
                sender.sendMessage(Text.PREFIX + (engine.quarantinePlayer(online.uniqueId(), online.name(), actor)
                        ? "&aPutting " + Text.escape(online.name()) + " in quarantine (exempt players stay free)."
                        : "&cAlready in quarantine."));
            }
        } else {
            sender.sendMessage(Text.PREFIX + "&cUsage: &f/" + engine.commands().label() + " " + usage());
        }
    }
}
