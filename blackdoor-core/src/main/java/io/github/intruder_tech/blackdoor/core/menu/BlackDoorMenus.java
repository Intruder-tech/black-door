package io.github.intruder_tech.blackdoor.core.menu;

import io.github.intruder_tech.blackdoor.config.ConfigError;
import io.github.intruder_tech.blackdoor.config.ReloadResult;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.settings.SettingsService;
import io.github.intruder_tech.blackdoor.core.settings.SettingsService.Setting;
import io.github.intruder_tech.blackdoor.packets.PacketCheck;
import io.github.intruder_tech.blackdoor.packets.PacketSecurityEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The in-game control panel. Pages are rebuilt from live state on every refresh. Features that
 * are not implemented yet are shown as locked with the phase that delivers them - never as
 * working toggles.
 */
public final class BlackDoorMenus {

    private final BlackDoorEngine engine;
    private final SettingsService settings;

    public BlackDoorMenus(BlackDoorEngine engine, SettingsService settings) {
        this.engine = engine;
        this.settings = settings;
    }

    public MenuPage main() {
        return session -> {
            List<MenuItem> items = new ArrayList<>();
            Setting mode = settings.find(SettingsService.GLOBAL_MODE).orElseThrow();
            boolean enforce = "enforce".equals(settings.current(mode));
            items.add(new MenuItem(10, enforce ? Icon.MODE_ENFORCE : Icon.MODE_MONITOR,
                    "&fGlobal mode: " + (enforce ? "&cENFORCE" : "&aMONITOR"),
                    List.of("&7" + mode.description(), "",
                            enforce ? "&cChecks in 'mitigate' mode block packets." : "&aNothing is blocked; findings are logged.",
                            "", "&eClick to switch"),
                    s -> cycle(s, mode)));
            items.add(new MenuItem(12, Icon.CATEGORY, "&fPacket checks",
                    List.of("&7Deterministic protocol validation", "&7(" + PacketCheck.values().length + " checks)", "",
                            "&eClick to open"),
                    s -> s.show(packetChecks())));
            items.add(locked(14, "Movement & combat checks", "Phase 3", "Physics-based fly, speed, reach and more"));
            items.add(locked(16, "Anti X-Ray / ESP", "Phase 4", "Ore obfuscation integration and entity hiding"));
            items.add(locked(20, "Network protection", "Phase 4", "Connection, ping and login flood limits"));
            items.add(new MenuItem(22, Icon.INFO, "&fStatus", statusLore(), null));
            items.add(new MenuItem(24, Icon.RELOAD, "&fReload configuration",
                    List.of("&7Re-reads all files (all-or-nothing).", "", "&eClick to reload"), this::reload));
            if (!engine.settings().setupCompleted()) {
                items.add(new MenuItem(4, Icon.SETUP, "&eSetup not completed",
                        List.of("&7Run &f/blackdoor setup &7for the", "&7initial security check."), null));
            }
            items.add(new MenuItem(26, Icon.CLOSE, "&cClose", List.of(), MenuSession::close));
            return new Menu("Black Door", 3, items);
        };
    }

    public MenuPage packetChecks() {
        return session -> {
            List<MenuItem> items = new ArrayList<>();
            Map<String, Long> counts = engine.ledger().map(l -> l.countsByCheck()).orElse(Map.of());
            boolean enforce = "enforce".equals(settings.current(settings.find(SettingsService.GLOBAL_MODE).orElseThrow()));
            int slot = 10;
            for (PacketCheck check : PacketCheck.values()) {
                Setting setting = settings.find(check.id()).orElseThrow();
                String current = settings.current(setting);
                Icon icon = switch (current) {
                    case "disabled" -> Icon.CHECK_DISABLED;
                    case "mitigate" -> Icon.CHECK_MITIGATE;
                    default -> Icon.CHECK_MONITOR;
                };
                List<String> lore = new ArrayList<>();
                lore.add("&7" + check.description());
                lore.add("");
                lore.add("&7Mode: " + colour(current) + current);
                lore.add("&7Confidence: &f" + check.confidence().name().toLowerCase(Locale.ROOT));
                lore.add("&7Findings since start: &f" + counts.getOrDefault(check.id(), 0L));
                if (current.equals("mitigate") && !enforce) {
                    lore.add("&8Blocks only when global mode is enforce.");
                }
                lore.add("");
                lore.add("&eClick: disabled > monitor > mitigate");
                items.add(new MenuItem(slot++, icon, "&f" + check.id(), lore, s -> cycle(s, setting)));
            }
            items.add(new MenuItem(31, Icon.BACK, "&7Back", List.of(), s -> s.show(main())));
            return new Menu("Black Door - Packet checks", 4, items);
        };
    }

    private void cycle(MenuSession session, Setting setting) {
        if (!session.viewer().hasPermission(SettingsService.EDIT_PERMISSION)) {
            session.viewer().sendMessage(Text.PREFIX + "&cYou can view but not change settings ("
                    + SettingsService.EDIT_PERMISSION + ").");
            return;
        }
        String next = settings.next(setting);
        String actor = session.viewer().name();
        boolean queued = settings.change(setting, next, actor, result -> {
            report(session, setting, next, result);
            session.refresh();
        });
        if (!queued) {
            session.viewer().sendMessage(Text.PREFIX + "&cBlack Door is busy; try again.");
        }
    }

    private static void report(MenuSession session, Setting setting, String value, ReloadResult result) {
        if (result.success()) {
            session.viewer().sendMessage(Text.PREFIX + "&a" + setting.label() + " set to &f" + value + "&a.");
        } else {
            session.viewer().sendMessage(Text.PREFIX + "&cChange rejected: " + result.errors().stream()
                    .findFirst().map(ConfigError::toString).map(Text::escape).orElse("unknown error"));
        }
    }

    private void reload(MenuSession session) {
        if (!session.viewer().hasPermission("blackdoor.command.reload")) {
            session.viewer().sendMessage(Text.PREFIX + "&cYou do not have permission to reload.");
            return;
        }
        String actor = session.viewer().name();
        boolean queued = engine.submitWork(() -> {
            ReloadResult result = engine.reloadConfiguration(actor);
            session.viewer().sendMessage(Text.PREFIX
                    + (result.success() ? "&aConfiguration reloaded." : "&cReload failed; see the server log."));
            session.refresh();
        });
        if (!queued) {
            session.viewer().sendMessage(Text.PREFIX + "&cBlack Door is busy; try again.");
        }
    }

    private List<String> statusLore() {
        List<String> lore = new ArrayList<>();
        lore.add("&7State: &f" + engine.state());
        engine.ledger().ifPresent(l -> lore.add("&7Findings: &f" + l.total()));
        engine.packetEngine().ifPresent(p -> {
            PacketSecurityEngine.Stats stats = p.stats();
            lore.add("&7Tracked connections: &f" + stats.sessions());
            lore.add("&7Packets inspected: &f" + stats.processed());
            lore.add("&7p99 inspection time: &f< " + Math.max(1, stats.p99Nanos() / 1000) + " us");
        });
        for (String reason : engine.degradedReasons()) {
            lore.add("&e" + Text.escape(reason));
        }
        return lore;
    }

    private static MenuItem locked(int slot, String name, String phase, String what) {
        return new MenuItem(slot, Icon.LOCKED, "&8" + name,
                List.of("&7" + what, "", "&8Not available yet - coming in " + phase), null);
    }

    private static String colour(String mode) {
        return switch (mode) {
            case "disabled" -> "&8";
            case "mitigate" -> "&c";
            default -> "&a";
        };
    }
}
