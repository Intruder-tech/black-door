package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.enforcement.BanBook;
import io.github.intruder_tech.blackdoor.enforcement.CaseBook;

import java.util.List;

/** Staff review of what Black Door did to players. Judging a case needs the bans permission. */
public final class CasesCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public CasesCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "cases";
    }

    @Override
    public String permission() {
        return "blackdoor.command.cases";
    }

    @Override
    public String description() {
        return "Review kicks and bans";
    }

    @Override
    public String usage() {
        return "cases [number] [false|real]";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        CaseBook book = engine.cases().orElse(null);
        if (book == null) {
            sender.sendMessage(Text.PREFIX + "&cCases are only available on game servers.");
            return;
        }
        if (args.isEmpty()) {
            List<CaseBook.Case> newest = book.newest(10);
            sender.sendMessage(Text.PREFIX + (newest.isEmpty() ? "&7No cases yet." : "&fNewest cases:"));
            newest.forEach(c -> sender.sendMessage(" " + summary(c)));
            return;
        }
        CaseBook.Case found = book.find(number(args.get(0))).orElse(null);
        String verdict = args.size() == 2 ? args.get(1).toLowerCase(java.util.Locale.ROOT) : null;
        if (found == null || args.size() > 2 || verdict != null && !verdict.equals("false") && !verdict.equals("real")) {
            sender.sendMessage(Text.PREFIX + "&cNo such case, or wrong form. Use &f/" + engine.commands().label()
                    + " " + usage());
        } else if (verdict == null) {
            sender.sendMessage(Text.PREFIX + summary(found));
            found.evidence().forEach(e -> sender.sendMessage(" &8- &7" + Text.escape(e.checkId()) + ": &f"
                    + Text.escape(e.detail()) + (e.blocked() ? " &8(blocked)" : " &8(only watched)")));
        } else if (!sender.hasPermission("blackdoor.command.bans")) {
            sender.sendMessage(Text.PREFIX + "&cYou can read cases, but not judge them (blackdoor.command.bans).");
        } else {
            boolean falseAlarm = verdict.equals("false");
            engine.judgeCase(found.id(), falseAlarm, sender.isConsole() ? "console" : sender.name());
            sender.sendMessage(Text.PREFIX + "&aCase #" + found.id()
                    + (falseAlarm ? " marked as a false alarm." : " marked as real."));
        }
    }

    private String summary(CaseBook.Case c) {
        String what = switch (c.action()) {
            case WOULD_KICK -> "would be kicked";
            case KICK -> "kicked";
            case BAN -> "banned";
        };
        return "&f#" + c.id() + " " + Text.escape(c.name()) + " &8- &7" + what + ", "
                + BanBook.words(engine.time().currentTimeMillis() - c.timeMillis()) + " ago"
                + (c.falseAlarm() ? " &a(false alarm)" : "");
    }

    /** The case number typed, or -1 which no case has. */
    private static int number(String text) {
        try {
            return Integer.parseInt(text.startsWith("#") ? text.substring(1) : text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
