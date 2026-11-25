package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.EngineState;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.config.GeneralSettings;
import io.github.intruder_tech.blackdoor.packets.PacketCheck;
import io.github.intruder_tech.blackdoor.packets.PacketSecurityEngine;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;
import io.github.intruder_tech.blackdoor.protocol.VersionSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Framed, colour-coded overview of Black Door's health. */
public final class StatusCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public StatusCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String permission() {
        return "blackdoor.command.status";
    }

    @Override
    public String description() {
        return "Health and protection overview";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        render().forEach(sender::sendMessage);
    }

    List<String> render() {
        String sep = " &8" + Text.BULLET + " ";
        List<String> lines = new ArrayList<>();
        EngineState state = engine.state();
        GeneralSettings settings = engine.settings();
        lines.add(Text.header("Status"));
        lines.add(Text.row("State", switch (state) {
            case RUNNING -> "&a" + Text.CHECK + " Running normally";
            case DEGRADED -> "&e! Running with problems";
            default -> "&c" + Text.CROSS + " " + state.name().toLowerCase(Locale.ROOT);
        } + sep + "&7Uptime &f" + Durations.human(engine.time().currentTimeMillis() - engine.startedAtMillis())));
        lines.add(Text.row("Protection", (settings.mode() == GeneralSettings.Mode.ENFORCE ? "&aON" : "&eOFF &7(watch only)")
                + sep + "&7Profile &f" + settings.profile().name().toLowerCase(Locale.ROOT).replace('_', '-')));
        lines.add(Text.row("Config", engine.configSnapshot().defaultsOnly()
                ? "&e! Files invalid - using safe defaults"
                : "&a" + Text.CHECK + " Loaded" + engine.lastReload().map(r -> r.success() ? "" : " &e(last reload failed)").orElse("")));
        if (!engine.platform().info().type().isProxy()) {
            ProtocolBridge protocol = engine.platform().protocol();
            lines.add(Text.row("Packet layer", protocol.isAvailable()
                    ? "&a" + Text.CHECK + " " + protocol.name() + " &8" + protocol.libraryVersion()
                    : "&c" + Text.CROSS + " Not installed &7(install PacketEvents)"));
        }
        engine.platform().info().minecraft().ifPresent(v -> {
            VersionSupport.Status support = VersionSupport.classify(v);
            lines.add(Text.row("Minecraft", "&b" + v + sep + (support == VersionSupport.Status.KNOWN
                    ? "&a" + Text.CHECK + " supported" : "&e! " + support.name().toLowerCase(Locale.ROOT).replace('_', ' '))));
        });
        engine.packetEngine().ifPresent(p -> {
            PacketSecurityEngine.Stats s = p.stats();
            long findings = engine.ledger().map(l -> l.total()).orElse(0L);
            lines.add(Text.row("Checks", "&a" + PacketCheck.values().length + " active" + sep
                    + String.format(Locale.ROOT, "&f%,d &7packets", s.processed()) + sep
                    + (findings == 0 ? "&a0" : "&c" + findings) + " &7findings" + sep + "&f" + s.dropped() + " &7blocked"));
            p.worldCache().ifPresent(w -> {
                var ws = w.stats();
                lines.add(Text.row("World view", String.format(Locale.ROOT, "&f%,d &7chunks", ws.chunks()) + sep
                        + String.format(Locale.ROOT, "&f%.1f &7/ %d MiB", ws.usedBytes() / 1048576.0, ws.budgetBytes() / 1048576)
                        + (ws.refused() > 0 ? sep + "&e" + ws.refused() + " refused" : "")));
            });
        });
        engine.workerStats().ifPresent(w -> lines.add(Text.row("Workers", "&f" + w.queued() + "&7/" + w.queueCapacity()
                + " queued" + sep + (w.failed() == 0 ? "&a0" : "&c" + w.failed()) + " &7failed")));
        lines.add(Text.row("Coming next", "&8Movement & combat checks (Phase 3)"));
        for (String reason : engine.degradedReasons()) {
            lines.add(Text.row("Problem", "&e! " + Text.escape(reason)));
        }
        if (!settings.setupCompleted()) {
            lines.add(Text.row("Setup", "&e! Not done - run &f/" + engine.commands().label() + " setup"));
        }
        lines.add(Text.footer());
        return lines;
    }
}
