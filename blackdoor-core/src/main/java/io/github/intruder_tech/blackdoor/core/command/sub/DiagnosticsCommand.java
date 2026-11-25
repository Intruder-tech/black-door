package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.common.concurrent.BoundedExecutor;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.packets.PacketSecurityEngine;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;
import io.github.intruder_tech.blackdoor.protocol.ProtocolFeature;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Technical card for administrators. Never prints secrets or file-system paths (configuration
 * fingerprints are truncated hashes).
 */
public final class DiagnosticsCommand implements Subcommand {

    private static final long MIB = 1024L * 1024L;

    private final BlackDoorEngine engine;

    public DiagnosticsCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "diagnostics";
    }

    @Override
    public String permission() {
        return "blackdoor.command.diagnostics";
    }

    @Override
    public String description() {
        return "Technical details for admins";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        String sep = " &8" + Text.BULLET + " ";
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / MIB;
        long max = rt.maxMemory() / MIB;
        sender.sendMessage(Text.header("Diagnostics"));
        sender.sendMessage(Text.row("Java", "&f" + Runtime.version() + sep + "&f" + rt.availableProcessors() + " &7CPU threads"));
        sender.sendMessage(Text.row("Server heap", (used * 100 / Math.max(1, max) < 80 ? "&a" : "&e") + used + " &7/ " + max + " MiB"));
        ProtocolBridge protocol = engine.platform().protocol();
        sender.sendMessage(Text.row("Packet library", protocol.isAvailable()
                ? "&a" + Text.CHECK + " &f" + Text.escape(protocol.name()) + " &8" + Text.escape(protocol.libraryVersion())
                : "&c" + Text.CROSS + " unavailable"));
        engine.platform().info().minecraft().ifPresent(v -> sender.sendMessage(Text.row("Protocol features", "&f" + features(v))));
        engine.packetEngine().ifPresent(p -> {
            PacketSecurityEngine.Stats s = p.stats();
            sender.sendMessage(Text.row("Inspection time", "&f< " + micros(s.p50Nanos()) + " &7typical" + sep
                    + "&f< " + micros(s.p99Nanos()) + " &7worst 1%" + sep + (s.errors() == 0 ? "&a0" : "&c" + s.errors()) + " &7errors"));
            sender.sendMessage(Text.row("Connections", "&f" + s.sessions() + " &7tracked" + sep
                    + (s.sessionsRejected() == 0 ? "&a0" : "&e" + s.sessionsRejected()) + " &7over limit"));
            sender.sendMessage(Text.row("Hidden pings", String.format(Locale.ROOT, "&f%,d &7sent", s.transactionsSent()) + sep
                    + String.format(Locale.ROOT, "&f%,d &7answered", s.transactionsAcknowledged())));
        });
        engine.workerStats().ifPresent(w -> sender.sendMessage(Text.row("Workers", describe(w, sep))));
        engine.audit().ifPresent(a -> sender.sendMessage(Text.row("Audit log", "&f" + a.written() + " &7written" + sep
                + (a.dropped() == 0 ? "&a0" : "&c" + a.dropped()) + " &7dropped")));
        for (Map.Entry<String, String> e : engine.configSnapshot().fingerprints().entrySet()) {
            String hash = e.getValue();
            sender.sendMessage(Text.row(e.getKey(), "&8" + (hash.length() > 12 ? hash.substring(0, 12) : hash)));
        }
        long threads = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith(BoundedExecutor.THREAD_PREFIX)).count();
        sender.sendMessage(Text.row("Black Door threads", "&f" + threads));
        sender.sendMessage(Text.footer());
    }

    private static String micros(long nanos) {
        return Math.max(1, nanos / 1000) + " us";
    }

    private static String features(MinecraftVersion version) {
        StringJoiner joiner = new StringJoiner(", ");
        for (ProtocolFeature f : ProtocolFeature.values()) {
            if (f.side() == ProtocolFeature.Side.SERVER && f.presentIn(version)) {
                joiner.add(f.name().toLowerCase(Locale.ROOT).replace('_', ' ') + (f.verified() ? "" : " (unverified)"));
            }
        }
        return joiner.length() == 0 ? "none" : joiner.toString();
    }

    private static String describe(BoundedExecutor.Stats s, String sep) {
        return "&f" + s.threads() + " &7threads" + sep + "&f" + s.queued() + "&7/" + s.queueCapacity() + " queued" + sep
                + "&f" + s.completed() + " &7done" + sep + (s.failed() == 0 ? "&a0" : "&c" + s.failed()) + " &7failed";
    }
}
