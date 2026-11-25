package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.common.concurrent.BoundedExecutor;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;
import io.github.intruder_tech.blackdoor.protocol.ProtocolFeature;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Technical diagnostics for administrators. Never prints secrets or file-system paths
 * (configuration fingerprints are truncated hashes).
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
        return "Shows JVM, platform, protocol and worker diagnostics.";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        Runtime rt = Runtime.getRuntime();
        sender.sendMessage(Text.PREFIX + "&fDiagnostics");
        sender.sendMessage("&8 - &7JVM: &f" + Text.escape(System.getProperty("java.vm.name", "?")) + " "
                + Runtime.version() + " &7| CPUs: &f" + rt.availableProcessors());
        sender.sendMessage("&8 - &7Heap: &f" + (rt.totalMemory() - rt.freeMemory()) / MIB + " / "
                + rt.maxMemory() / MIB + " MiB");
        ProtocolBridge protocol = engine.platform().protocol();
        sender.sendMessage("&8 - &7Protocol bridge: &f" + Text.escape(protocol.name()) + " "
                + Text.escape(protocol.libraryVersion()) + (protocol.isAvailable() ? " &a(available)" : " &c(unavailable)"));
        engine.platform().info().minecraft()
                .ifPresent(v -> sender.sendMessage("&8 - &7Server features: &f" + features(v)));
        engine.workerStats().ifPresent(s -> sender.sendMessage("&8 - &7Workers: &f" + describe(s)));
        engine.audit().ifPresent(a -> sender.sendMessage("&8 - &7Audit: &f" + a.written() + " written, "
                + a.dropped() + " dropped"));
        for (Map.Entry<String, String> e : engine.configSnapshot().fingerprints().entrySet()) {
            String hash = e.getValue();
            sender.sendMessage("&8 - &7" + e.getKey() + ": &f" + (hash.length() > 12 ? hash.substring(0, 12) : hash));
        }
        long threads = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith(BoundedExecutor.THREAD_PREFIX)).count();
        sender.sendMessage("&8 - &7Black Door threads: &f" + threads);
    }

    private static String features(MinecraftVersion version) {
        StringJoiner joiner = new StringJoiner(", ");
        for (ProtocolFeature f : ProtocolFeature.values()) {
            if (f.side() == ProtocolFeature.Side.SERVER && f.presentIn(version)) {
                joiner.add(f.name().toLowerCase(Locale.ROOT) + (f.verified() ? "" : " (unverified)"));
            }
        }
        return joiner.length() == 0 ? "(none)" : joiner.toString();
    }

    private static String describe(BoundedExecutor.Stats s) {
        return s.threads() + " thread(s), " + s.active() + " active, " + s.queued() + "/" + s.queueCapacity()
                + " queued, " + s.completed() + " done, " + s.rejected() + " rejected, " + s.failed() + " failed";
    }
}
