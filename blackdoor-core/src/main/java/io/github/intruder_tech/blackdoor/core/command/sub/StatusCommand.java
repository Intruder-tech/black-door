package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.EngineState;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.config.GeneralSettings;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;
import io.github.intruder_tech.blackdoor.protocol.VersionSupport;

import java.util.List;
import java.util.Locale;

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
        return "Shows engine state, mode and component health.";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        EngineState state = engine.state();
        GeneralSettings settings = engine.settings();
        String colour = state == EngineState.RUNNING ? "&a" : state == EngineState.DEGRADED ? "&e" : "&c";
        sender.sendMessage(Text.PREFIX + "&fStatus: " + colour + state);
        sender.sendMessage("&8 - &7Mode: &f" + lower(settings.mode()) + " &7| Profile: &f" + lower(settings.profile()));
        sender.sendMessage("&8 - &7Uptime: &f" + Durations.human(engine.time().currentTimeMillis() - engine.startedAtMillis()));
        sender.sendMessage("&8 - &7Configuration: &f" + (engine.configSnapshot().defaultsOnly() ? "&ebundled defaults" : "loaded")
                + engine.lastReload().map(r -> r.success() ? "" : " &c(last reload failed)").orElse(""));
        ProtocolBridge protocol = engine.platform().protocol();
        if (!engine.platform().info().type().isProxy()) {
            sender.sendMessage("&8 - &7Packet layer: " + (protocol.isAvailable() ? "&aavailable" : "&cunavailable")
                    + " &7(" + Text.escape(protocol.name()) + ")");
        }
        engine.platform().info().minecraft().ifPresent(v -> sender.sendMessage("&8 - &7Minecraft " + v + ": &f"
                + lower(VersionSupport.classify(v))));
        engine.workerStats().ifPresent(s -> sender.sendMessage("&8 - &7Workers: &f" + s.queued() + "/" + s.queueCapacity()
                + " queued, " + s.rejected() + " rejected, " + s.failed() + " failed"));
        sender.sendMessage("&8 - &7Protections active: &fnone yet &7(Phase 1 foundation build)");
        for (String reason : engine.degradedReasons()) {
            sender.sendMessage("&8 - &eDegraded: &7" + Text.escape(reason));
        }
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
