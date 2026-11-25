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
        return "Runs the initial security check and finishes setup.";
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
            if (Boolean.TRUE.equals(facts.onlineMode())) {
                lines.add(ok("Online mode is on (Mojang authentication)"));
            } else if (Boolean.FALSE.equals(facts.onlineMode())) {
                if (Boolean.TRUE.equals(facts.bungeeCordForwarding())) {
                    lines.add(warn("Behind BungeeCord: firewall this server so ONLY the proxy can connect, or anyone can spoof names/UUIDs."));
                } else {
                    lines.add(fail("Offline mode: unless this server is behind a Velocity proxy with modern forwarding,"
                            + " anyone can join as any player."));
                }
                if (Boolean.TRUE.equals(facts.boundToAllInterfaces())) {
                    lines.add(warn("server-ip is empty (listens on every interface); bind it to your private network."));
                }
            } else {
                lines.add(warn("Could not read online-mode on this platform; check it manually."));
            }
        } else {
            lines.add(warn("Proxy: forwarding validation and connection limits arrive in Phase 4."));
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
