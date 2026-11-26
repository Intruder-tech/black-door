package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.config.ReloadResult;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.config.CoreConfig;
import io.github.intruder_tech.blackdoor.core.config.GeneralSettings;
import io.github.intruder_tech.blackdoor.core.platform.SecurityFacts;
import io.github.intruder_tech.blackdoor.core.settings.SettingsService;
import io.github.intruder_tech.blackdoor.protocol.VersionSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One-command initial setup: checks the server's security basics, records the chosen profile,
 * marks setup as done and points the operator to the in-game control panel. It never weakens
 * anything: the global mode stays as configured (monitor by default).
 */
public final class SetupCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public SetupCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "setup";
    }

    @Override
    public String permission() {
        return "blackdoor.command.setup";
    }

    @Override
    public String description() {
        return "First-time security check";
    }

    @Override
    public String usage() {
        return "setup [profile]";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        Optional<String> profile = Optional.empty();
        if (!args.isEmpty()) {
            String wanted = args.get(0).toLowerCase(Locale.ROOT);
            if (!profiles().contains(wanted)) {
                sender.sendMessage(Text.PREFIX + "&cUnknown profile. Options: &f" + String.join(", ", profiles()));
                return;
            }
            profile = Optional.of(wanted);
        }
        sender.sendMessage(Text.PREFIX + "&fInitial security check");
        for (String line : report()) {
            sender.sendMessage(line);
        }
        String actor = sender.isConsole() ? "console" : sender.name();
        Optional<String> chosen = profile;
        boolean queued = engine.submitWork(() -> {
            if (chosen.isPresent()) {
                ReloadResult r = engine.applyChange(CoreConfig.GENERAL, "profile", chosen.get(), actor);
                sender.sendMessage(Text.PREFIX + (r.success() ? "&aProfile set to &f" + chosen.get() + "&a." : "&cCould not set profile; see the server log."));
            }
            ReloadResult done = engine.applyChange(CoreConfig.GENERAL, "setup.completed", "true", actor);
            if (!done.success()) {
                sender.sendMessage(Text.PREFIX + "&cSetup could not be saved; see the server log.");
                return;
            }
            sender.sendMessage(Text.PREFIX + "&aSetup complete. &7Protection is &f"
                    + (engine.settings().mode() == GeneralSettings.Mode.ENFORCE ? "ON" : "OFF (watch only)") + "&7 - you can switch it in the control panel.");
            sender.sendMessage(Text.PREFIX + "&fNext: open the control panel with &e/" + engine.commands().label() + " gui"
                    + (sender.isConsole() ? " &7(in game)" : ""));
            sender.sendMessage("&8   Players need &7blackdoor.command.gui &8to view and &7" + SettingsService.EDIT_PERMISSION
                    + " &8to change settings.");
        });
        if (!queued) {
            sender.sendMessage(Text.PREFIX + "&cBlack Door is busy; try again.");
        }
    }

    /** One line per check, prefixed OK / WARN / FAIL. Visible for tests. */
    List<String> report() {
        List<String> lines = new ArrayList<>();
        boolean proxy = engine.platform().info().type().isProxy();
        if (!proxy) {
            lines.add(engine.platform().protocol().isAvailable()
                    ? ok("Packet layer available (" + engine.platform().protocol().name() + ")")
                    : fail("PacketEvents is not installed: install it, then restart. Packet checks are inactive."));
            engine.platform().info().minecraft().ifPresent(v -> {
                VersionSupport.Status status = VersionSupport.classify(v);
                lines.add(status == VersionSupport.Status.KNOWN ? ok("Minecraft " + v + " is supported")
                        : warn("Minecraft " + v + " is " + status.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
            });
            SecurityFacts facts = engine.platform().securityFacts();
            forwarding(facts, lines);
            antiXray(lines);
        } else {
            lines.add(ok("Proxy: join limits follow network.yml (mode " + engine.networkSettings().mode()
                    .name().toLowerCase(Locale.ROOT) + ")"));
        }
        lines.add(engine.configSnapshot().defaultsOnly()
                ? fail("Configuration files are invalid; bundled defaults are active. Fix them and reload.")
                : ok("Configuration valid"));
        GeneralSettings s = engine.settings();
        lines.add(s.mode() == GeneralSettings.Mode.MONITOR
                ? ok("Global mode: monitor (recommended until you have reviewed findings)")
                : warn("Global mode: enforce (checks in 'mitigate' mode block packets)"));
        lines.add(s.auditEnabled() ? ok("Audit log enabled") : warn("Audit log disabled: admin actions are not recorded"));
        return lines;
    }

    /** Online mode, proxy forwarding and whether the proxy can be bypassed. */
    private void forwarding(SecurityFacts facts, List<String> lines) {
        boolean velocity = Boolean.TRUE.equals(facts.velocityForwarding());
        if (Boolean.TRUE.equals(facts.onlineMode())) {
            lines.add(ok("Online mode is on (Mojang authentication)"));
        } else if (facts.onlineMode() == null) {
            lines.add(warn("Could not read online-mode on this platform; check it manually."));
        } else if (Boolean.TRUE.equals(facts.bungeeCordForwarding())) {
            lines.add(warn("Behind BungeeCord: firewall this server so ONLY the proxy can connect, or anyone can spoof names/UUIDs."));
        } else if (!velocity) {
            lines.add(fail("Offline mode without proxy forwarding: anyone can join as any player."));
        }
        if (velocity) {
            lines.add(Boolean.TRUE.equals(facts.weakVelocitySecret())
                    ? fail("Velocity forwarding secret is empty or too short: set a long random one on both sides.")
                    : ok("Velocity modern forwarding is on"));
        }
        if (facts.behindProxy()) {
            int proxies = engine.networkSettings().allowedProxies().size();
            lines.add(proxies == 0
                    ? warn("Set allowed-proxies in network.yml to your proxy's address so nobody can bypass it.")
                    : ok("Proxy list set (" + proxies + "); " + directJoinHandling()));
        }
        if (Boolean.FALSE.equals(facts.onlineMode()) && Boolean.TRUE.equals(facts.boundToAllInterfaces())) {
            lines.add(warn("server-ip is empty (listens on every interface); bind it to your private network."));
        }
    }

    /** Black Door does not hide ores itself; it checks that Paper does. */
    private void antiXray(List<String> lines) {
        var files = engine.antiXrayFiles();
        if (files.isEmpty()) {
            lines.add(warn("Anti-xray not checked on this server (Paper only)."));
            return;
        }
        var found = engine.antiXrayStatus(files.get());
        if (found.isEmpty()) {
            lines.add(warn("Paper's anti-xray files could not be read (config/paper-world-defaults.yml and the"
                    + " worlds' paper-world.yml)."));
            return;
        }
        var status = found.get();
        List<String> off = status.configuredOff();
        String panel = "the Anti X-Ray item in /" + engine.commands().label() + " gui";
        if (!off.isEmpty() && status.changedSinceStart()) {
            lines.add(warn("Paper anti-xray was switched off in: " + String.join(", ", off) + ". That applies after the next"
                    + " restart. To keep it on, switch it back with " + panel + " before restarting."));
        } else if (!off.isEmpty()) {
            lines.add(warn("Paper anti-xray is off in: " + String.join(", ", off) + ". Turn it on with " + panel
                    + ", then restart the server."));
        } else if (status.pending()) {
            List<String> stillOff = status.runningOff();
            lines.add(warn("Paper anti-xray is set to on but not running yet ("
                    + (stillOff.isEmpty() ? "unknown in: " + String.join(", ", status.runningUnknown())
                    : "off in: " + String.join(", ", stillOff)) + "): restart the server."));
        } else {
            lines.add(ok("Paper anti-xray is on in every world"));
        }
    }

    private String directJoinHandling() {
        return switch (engine.networkSettings().mode()) {
            case DISABLED -> "but join protection is Off, so others are not stopped";
            case MONITOR -> "others are only logged (join protection is on Watch)";
            case MITIGATE -> engine.settings().mode() == GeneralSettings.Mode.ENFORCE
                    ? "others are blocked" : "others will be blocked once Protection is ON";
        };
    }

    @Override
    public List<String> complete(BdSender sender, List<String> args) {
        if (args.size() != 1) {
            return List.of();
        }
        return profiles().stream().filter(p -> p.startsWith(args.get(0).toLowerCase(Locale.ROOT))).toList();
    }

    private static List<String> profiles() {
        List<String> out = new ArrayList<>();
        for (GeneralSettings.Profile p : GeneralSettings.Profile.values()) {
            out.add(p.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return out;
    }

    private static String ok(String text) {
        return "&8 [&aOK&8] &7" + text;
    }

    private static String warn(String text) {
        return "&8 [&eWARN&8] &7" + text;
    }

    private static String fail(String text) {
        return "&8 [&cFAIL&8] &7" + text;
    }
}
