package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.config.GeneralSettings;
import io.github.intruder_tech.blackdoor.core.menu.BlackDoorMenus;
import io.github.intruder_tech.blackdoor.packets.CheckMode;
import io.github.intruder_tech.blackdoor.packets.PacketCheck;

import java.util.List;
import java.util.Map;

/** Card listing every check with its plain name, mode and finding count. */
public final class ChecksCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public ChecksCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "checks";
    }

    @Override
    public String permission() {
        return "blackdoor.command.checks";
    }

    @Override
    public String description() {
        return "All checks and their modes";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        String sep = " &8" + Text.BULLET + " ";
        Map<String, Long> counts = engine.ledger().map(l -> l.countsByCheck()).orElse(Map.of());
        boolean on = engine.settings().mode() == GeneralSettings.Mode.ENFORCE;
        sender.sendMessage(Text.header("Checks"));
        sender.sendMessage(Text.row("Protection", on ? "&aON &7(Block checks stop actions)" : "&eOFF &7(watch only)"));
        for (PacketCheck check : PacketCheck.values()) {
            CheckMode mode = engine.packetSettings().mode(check);
            long n = counts.getOrDefault(check.id(), 0L);
            sender.sendMessage(" &8" + Text.ARROW + " &f" + BlackDoorMenus.friendlyName(check.id()) + "&7: " + label(mode)
                    + sep + (n == 0 ? "&a0" : "&c" + n) + " &7caught &8(" + check.id() + ")");
        }
        sender.sendMessage(Text.row("Coming next", "&8Movement, combat, world & inventory checks (Phase 3)"));
        sender.sendMessage(" &8Change modes in &7/" + engine.commands().label() + " gui");
        sender.sendMessage(Text.footer());
    }

    private static String label(CheckMode mode) {
        return switch (mode) {
            case DISABLED -> "&8Off";
            case MONITOR -> "&aWatch";
            case MITIGATE -> "&cBlock";
        };
    }
}
