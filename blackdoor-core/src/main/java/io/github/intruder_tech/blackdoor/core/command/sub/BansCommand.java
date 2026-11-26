package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.enforcement.BanBook;

import java.util.List;

/** Staff view of Black Door's temporary bans, and the way to lift one. */
public final class BansCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public BansCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "bans";
    }

    @Override
    public String permission() {
        return "blackdoor.command.bans";
    }

    @Override
    public String description() {
        return "List or lift temporary bans";
    }

    @Override
    public String usage() {
        return "bans [unban <player>]";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        BanBook book = engine.bans().orElse(null);
        if (book == null) {
            sender.sendMessage(Text.PREFIX + "&cTemporary bans are only available on game servers.");
        } else if (args.isEmpty()) {
            List<BanBook.Ban> bans = book.active();
            sender.sendMessage(Text.PREFIX + (bans.isEmpty() ? "&7Nobody is banned by Black Door."
                    : "&fTemporary bans: " + bans.size()));
            long now = engine.time().currentTimeMillis();
            bans.stream().limit(20).forEach(b -> sender.sendMessage(" &f" + Text.escape(b.name())
                    + " &8- &7" + BanBook.words(b.untilMillis() - now) + " left"));
        } else if (args.size() == 2 && args.get(0).equalsIgnoreCase("unban")) {
            boolean lifted = engine.unban(args.get(1), sender.isConsole() ? "console" : sender.name());
            sender.sendMessage(Text.PREFIX + (lifted ? "&aUnbanned " + Text.escape(args.get(1)) + "."
                    : "&cNo temporary ban for &f" + Text.escape(args.get(1)) + "&c."));
        } else {
            sender.sendMessage(Text.PREFIX + "&cUsage: &f/" + engine.commands().label() + " " + usage());
        }
    }
}
