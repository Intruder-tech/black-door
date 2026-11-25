package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.packets.PacketCheck;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lists every check with its mode and finding count. */
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
        Map<String, Long> counts = engine.ledger().map(l -> l.countsByCheck()).orElse(Map.of());
        sender.sendMessage(Text.PREFIX + "&fChecks &7(global mode: &f"
                + engine.settings().mode().name().toLowerCase(Locale.ROOT) + "&7)");
        for (PacketCheck check : PacketCheck.values()) {
            String mode = engine.packetSettings().mode(check).name().toLowerCase(Locale.ROOT);
            sender.sendMessage("&8 - &f" + check.id() + " &7" + mode + " &8| &7findings: &f"
                    + counts.getOrDefault(check.id(), 0L));
        }
        sender.sendMessage("&8 - &7Movement/combat/world/inventory checks: &8planned (Phase 3)");
    }
}
