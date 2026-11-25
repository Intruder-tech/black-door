package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.state.SessionSnapshot;
import io.github.intruder_tech.blackdoor.violations.Subject;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Shows Black Door's packet-derived view of one connected player. */
public final class PlayerCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public PlayerCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "player";
    }

    @Override
    public String permission() {
        return "blackdoor.command.player";
    }

    @Override
    public String description() {
        return "Shows tracked state and findings for a player.";
    }

    @Override
    public String usage() {
        return "player <name>";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        if (args.size() != 1) {
            sender.sendMessage(Text.PREFIX + "&cUsage: /" + engine.commands().label() + " player <name>");
            return;
        }
        Optional<SessionSnapshot> found = engine.sessions().flatMap(s -> s.find(args.get(0)));
        if (found.isEmpty()) {
            sender.sendMessage(Text.PREFIX + "&cNo tracked connection for '" + Text.escape(args.get(0)) + "'.");
            return;
        }
        SessionSnapshot s = found.get();
        sender.sendMessage(Text.PREFIX + "&f" + Text.escape(String.valueOf(s.name())));
        sender.sendMessage("&8 - &7Client version: &f" + (s.clientVersion() == null ? "unknown" : s.clientVersion())
                + " &7| entity id: &f" + s.entityId());
        sender.sendMessage("&8 - &7Connected for: &f" + Durations.human(s.ageMillis()) + " &7| packets: &f" + s.inboundPackets());
        if (s.hasPosition()) {
            sender.sendMessage(String.format(Locale.ROOT, "&8 - &7Last position: &f%.2f %.2f %.2f &7yaw &f%.1f &7pitch &f%.1f &7%s",
                    s.x(), s.y(), s.z(), s.yaw(), s.pitch(), s.onGround() ? "on ground" : "in air"));
        }
        sender.sendMessage("&8 - &7Pending teleports: &f" + s.pendingTeleports());
        Map<String, Long> counts = engine.ledger().map(l -> l.countsForSubject(new Subject(s.uniqueId(), s.name()))).orElse(Map.of());
        sender.sendMessage("&8 - &7Findings: &f" + (counts.isEmpty() ? "none" : counts.toString()));
    }

    @Override
    public List<String> complete(BdSender sender, List<String> args) {
        if (args.size() != 1) {
            return List.of();
        }
        String prefix = args.get(0).toLowerCase(Locale.ROOT);
        return engine.sessions().map(s -> s.names().stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(50).toList()).orElse(List.of());
    }
}
