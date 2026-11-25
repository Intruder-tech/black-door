package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.menu.BlackDoorMenus;
import io.github.intruder_tech.blackdoor.state.SessionSnapshot;
import io.github.intruder_tech.blackdoor.violations.Subject;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shows Black Door's view of one connected player as a framed, colour-coded card: identity,
 * connection quality, position, what the client can see, and findings in plain language.
 */
public final class PlayerCommand implements Subcommand {

    /** Ping colour thresholds in milliseconds (green below the first, yellow below the second, red above). */
    static final double PING_GOOD = 80;
    static final double PING_FAIR = 150;

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
            sender.sendMessage(Text.PREFIX + "&cUsage: &f/" + engine.commands().label() + " player <name>");
            return;
        }
        Optional<SessionSnapshot> found = engine.sessions().flatMap(s -> s.find(args.get(0)));
        if (found.isEmpty()) {
            sender.sendMessage(Text.PREFIX + "&c" + Text.CROSS + " No online player named '&f"
                    + Text.escape(args.get(0)) + "&c' is being watched.");
            return;
        }
        String world = engine.sessions().flatMap(r -> r.findSession(args.get(0)))
                .flatMap(session -> engine.packetEngine().flatMap(p -> p.worldView(session)))
                .map(v -> "&f" + v.loadedChunks() + " &7chunks known &8" + Text.BULLET + " &7block changes pending &f"
                        + v.pendingChanges())
                .orElse(null);
        for (String line : render(found.get(), world)) {
            sender.sendMessage(line);
        }
    }

    List<String> render(SessionSnapshot s) {
        return render(s, null);
    }

    /** Builds the card. Package-private for tests. */
    List<String> render(SessionSnapshot s, String worldLine) {
        String sep = " &8" + Text.BULLET + " ";
        Map<String, Long> counts = engine.ledger()
                .map(l -> l.countsForSubject(new Subject(s.uniqueId(), s.name()))).orElse(Map.of());
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add(Text.header(Text.escape(String.valueOf(s.name()))));
        lines.add(Text.row("Client", "&b" + (s.clientVersion() == null ? "unknown" : s.clientVersion().toString())
                + sep + "&7Online for &f" + Durations.human(s.ageMillis())));
        lines.add(Text.row("Connection", pingText(s) + sep + "&7Unanswered pings &f" + s.outstandingTransactions()));
        if (s.hasPosition()) {
            lines.add(Text.row("Position", String.format(Locale.ROOT, "&f%.1f&8, &f%.1f&8, &f%.1f", s.x(), s.y(), s.z())
                    + sep + (s.onGround() ? "&aon ground" : "&ein the air")));
            lines.add(Text.row("Looking", String.format(Locale.ROOT, "&fyaw %.1f&8, &fpitch %.1f",
                    wrapYaw(s.yaw()), s.pitch())));
        } else {
            lines.add(Text.row("Position", "&8not known yet"));
        }
        lines.add(Text.row("Sees", "&f" + s.trackedEntities() + " &7entities" + sep
                + "&7Knockback waiting &f" + s.pendingVelocities() + sep + "&7Teleports waiting &f" + s.pendingTeleports()));
        if (worldLine != null) {
            lines.add(Text.row("World", worldLine));
        }
        lines.add(Text.row("Traffic", String.format(Locale.ROOT, "&f%,d &7packets checked", s.inboundPackets())
                + sep + "&7Entity id &8" + s.entityId()));
        if (counts.isEmpty()) {
            lines.add(Text.row("Findings", "&a" + Text.CHECK + " None - nothing suspicious seen"));
        } else {
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            lines.add(Text.row("Findings", "&c" + Text.CROSS + " " + total + " suspicious action(s)"));
            counts.forEach((check, n) -> lines.add("     &8" + Text.BULLET + " &c" + BlackDoorMenus.friendlyName(check)
                    + " &8x&f" + n + " &8(" + check + ")"));
        }
        lines.add(Text.footer());
        return lines;
    }

    private static String pingText(SessionSnapshot s) {
        if (s.rttMillis() < 0) {
            return "&8ping not measured yet";
        }
        String colour = s.rttMillis() < PING_GOOD ? "&a" : s.rttMillis() < PING_FAIR ? "&e" : "&c";
        return String.format(Locale.ROOT, "%s%.0f ms &7ping &8(jitter %.0f ms)", colour, s.rttMillis(), s.jitterMillis());
    }

    /** Shows yaw in the familiar -180..180 range instead of the raw ever-growing value. */
    static float wrapYaw(float yaw) {
        float wrapped = yaw % 360f;
        if (wrapped >= 180f) {
            wrapped -= 360f;
        } else if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
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
