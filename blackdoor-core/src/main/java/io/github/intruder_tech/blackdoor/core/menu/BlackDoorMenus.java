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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The in-game control panel, written for server owners who are not security experts: plain
 * names, short explanations, and modes shown as Off / Watch / Block. Pages are rebuilt from live
 * state on every refresh. Features that are not implemented yet are shown as locked with the
 * phase that delivers them - never as working toggles.
 */
public final class BlackDoorMenus {

    /** Plain-language name and explanation per check (the technical id is shown in small print). */
    record Friendly(String name, String line1, String line2) {
    }

    static final Map<PacketCheck, Friendly> FRIENDLY = new EnumMap<>(PacketCheck.class);

    static {
        FRIENDLY.put(PacketCheck.INVALID_POSITION, new Friendly("Impossible location",
                "Player claims to stand somewhere that", "cannot exist (broken or out-of-world numbers)."));
        FRIENDLY.put(PacketCheck.INVALID_ROTATION, new Friendly("Impossible head angle",
                "Player looks further up or down than", "the game allows. Typical of hacked clients."));
        FRIENDLY.put(PacketCheck.INVALID_SLOT, new Friendly("Invalid hotbar slot",
                "Player selects a hotbar slot that", "does not exist. Only hacked clients do this."));
        FRIENDLY.put(PacketCheck.SELF_INTERACT, new Friendly("Hitting themselves",
                "Player attacks or uses their own", "character, which normal play cannot do."));
        FRIENDLY.put(PacketCheck.TELEPORT_CONFIRM, new Friendly("Fake teleport reply",
                "Player answers a teleport the server", "never sent. Used by some fly/phase hacks."));
        FRIENDLY.put(PacketCheck.OVERSIZED, new Friendly("Oversized messages",
                "Chat, commands or movement data bigger", "than the game ever sends. Can lag or crash."));
        FRIENDLY.put(PacketCheck.TRANSACTION, new Friendly("Fake connection replies",
                "Player answers Black Door's hidden pings", "wrongly - a sign of lag or timer cheats."));
        FRIENDLY.put(PacketCheck.FLOOD, new Friendly("Spam / flooding",
                "Far more actions per second than normal", "play. Can lag the server."));
        FRIENDLY.put(PacketCheck.PAYLOAD, new Friendly("Oversized mod data",
                "Hidden data from the player's game or", "mods that is far too big. Can crash."));
        FRIENDLY.put(PacketCheck.BOOK, new Friendly("Crash books",
                "Books with too many pages or too much", "text. Used to lag or crash servers."));
        FRIENDLY.put(PacketCheck.SIGN, new Friendly("Crash signs",
                "Sign text longer than the game", "allows. Only hacked clients send this."));
        FRIENDLY.put(PacketCheck.CREATIVE_ITEM, new Friendly("Illegal creative items",
                "Creative-mode items with impossible", "amounts, slots or huge hidden data."));
        FRIENDLY.put(PacketCheck.ITEM_DATA, new Friendly("Crash items",
                "Items with data nested far too deep", "or far too big. Can lag or crash."));
        FRIENDLY.put(PacketCheck.GAME_MODE, new Friendly("Fake creative mode",
                "Player takes creative-menu items while", "not in creative mode. Only hacks do this."));
    }

    /** Plain-language name for a check id (falls back to the id itself). */
    public static String friendlyName(String checkId) {
        for (Map.Entry<PacketCheck, Friendly> e : FRIENDLY.entrySet()) {
            if (e.getKey().id().equals(checkId)) {
                return e.getValue().name();
            }
        }
        return checkId;
    }

    private static Icon iconFor(PacketCheck check) {
        return switch (check) {
            case INVALID_POSITION -> Icon.POSITION;
            case INVALID_ROTATION -> Icon.ROTATION;
            case INVALID_SLOT -> Icon.SLOT;
            case SELF_INTERACT -> Icon.SELF_INTERACT;
            case TELEPORT_CONFIRM -> Icon.TELEPORT;
            case OVERSIZED -> Icon.OVERSIZED;
            case TRANSACTION -> Icon.TRANSACTION;
            case FLOOD -> Icon.FLOOD;
            case PAYLOAD -> Icon.PAYLOAD;
            case BOOK -> Icon.BOOK;
            case SIGN -> Icon.SIGN;
            case CREATIVE_ITEM -> Icon.CREATIVE_ITEM;
            case ITEM_DATA -> Icon.ITEM_DATA;
            case GAME_MODE -> Icon.GAME_MODE;
        };
    }

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
            boolean on = "enforce".equals(settings.current(mode));
            items.add(new MenuItem(10, on ? Icon.MODE_ENFORCE : Icon.MODE_MONITOR,
                    on ? "&fProtection: &aON" : "&fProtection: &eOFF &7(watch only)",
                    on ? List.of("&7Checks set to &cBlock &7stop cheating", "&7actions as they happen.", "",
                                    "&eClick to turn OFF (watch only)")
                            : List.of("&7Black Door only &fwatches and logs&7.", "&7Nobody is stopped or kicked.",
                                    "&7Recommended for the first few days.", "", "&eClick to turn ON"),
                    s -> cycle(s, mode, on ? "Protection is now OFF (watch only)" : "Protection is now ON")));
            items.add(new MenuItem(12, Icon.CATEGORY, "&fBasic cheat checks",
                    List.of("&7" + PacketCheck.values().length + " checks for actions only", "&7hacked clients can do.",
                            "", "&eClick to open"),
                    s -> s.show(packetChecks())));
            items.add(locked(14, Icon.LOCKED_COMBAT, "Movement & combat checks", "Fly, speed, reach, kill aura...", "Phase 3"));
            items.add(locked(16, Icon.LOCKED_XRAY, "Anti X-Ray / ESP", "Hide ores and players from cheaters", "Phase 4"));
            Setting joins = settings.find(SettingsService.NETWORK_MODE).orElseThrow();
            String joinMode = settings.current(joins);
            Setting login = settings.find(SettingsService.AUTH_ENABLED).orElseThrow();
            boolean loginOn = engine.authSettings().enabled();
            items.add(botFloodItem(22));
            items.add(new MenuItem(24, loginOn ? Icon.LOGIN_SYSTEM : Icon.CHECK_DISABLED,
                    "&fLogin system &8| " + (loginOn ? "&aOn" : "&8Off"),
                    List.of("&7Players must &f/signup &7once and", "&f/signin &7with their password.",
                            "&7Same account and address skip it", "&7for " + engine.authSettings().sessionHours() + " hours.",
                            "", "&eClick to turn " + (loginOn ? "off" : "on")),
                    s -> cycle(s, login, loginOn ? "Login system is now Off"
                            : "Login system is now On (applies from each player's next join)"), false));
            items.add(new MenuItem(20, joinMode.equals("disabled") ? Icon.CHECK_DISABLED : Icon.JOIN_GUARD,
                    "&fJoin flood protection &8| " + modeLabel(joinMode),
                    List.of("&7Limits how fast one address can", "&7join or ping your server.", "",
                            "&7Now: " + modeLabel(joinMode) + " &8- " + modeMeaning(joinMode),
                            "&7Proxy lock: " + (engine.networkSettings().allowedProxies().isEmpty()
                                    ? "&8not set" : "&aonly your proxy may connect") + " &8(network.yml)",
                            "&8Cannot stop large DDoS attacks.", "", "&eClick to change: &8Off > &aWatch &8> &cBlock"),
                    s -> cycle(s, joins, "Join flood protection is now " + plainMode(settings.next(joins))),
                    joinMode.equals("mitigate")));
            items.add(new MenuItem(30, Icon.STATUS, "&fStatus", statusLore(), null));
            items.add(new MenuItem(32, Icon.RELOAD,"&fReload settings",
                    List.of("&7Use this after editing the config", "&7files by hand.", "", "&eClick to reload"),
                    this::reload));
            if (!engine.settings().setupCompleted()) {
                items.add(new MenuItem(4, Icon.SETUP, "&eFinish setup first",
                        List.of("&7Type &f/blackdoor setup &7in chat.", "&7It checks your server's safety basics."), null));
            }
            items.add(new MenuItem(40, Icon.CLOSE, "&cClose", List.of(), MenuSession::close));
            return new Menu("Black Door", 5, items);
        };
    }

    private MenuItem botFloodItem(int slot) {
        Setting setting = settings.find(SettingsService.ADAPTIVE_MODE).orElseThrow();
        String mode = settings.current(setting);
        List<String> lore = new ArrayList<>();
        lore.add("&7Keeps regular players able to join");
        lore.add("&7while bots flood the server.");
        lore.add("");
        lore.add("&7Now: " + modeLabel(mode) + " &8- " + modeMeaning(mode));
        engine.connectionGuard().ifPresent(g -> {
            var attack = g.attackStatus();
            lore.add("&7Attack right now: " + (attack.active() ? "&cYes" : "&fNo"));
            lore.add("&7Known addresses: &f" + attack.knownAddresses());
            if (g.warmingUp()) {
                lore.add("&6Just started: refuses nothing yet.");
            }
        });
        boolean on = "enforce".equals(settings.current(settings.find(SettingsService.GLOBAL_MODE).orElseThrow()));
        if (mode.equals("mitigate") && !on) {
            lore.add("&6Will only block once Protection is ON.");
        }
        if (engine.networkSettings().mode()
                == io.github.intruder_tech.blackdoor.network.NetworkSettings.Mode.DISABLED) {
            lore.add("&6Inactive: join flood protection is Off.");
        }
        lore.add("");
        lore.add("&eClick to change: &8Off > &aWatch &8> &cBlock");
        return new MenuItem(slot, mode.equals("disabled") ? Icon.CHECK_DISABLED : Icon.BOT_FLOOD,
                "&fBot flood mode &8| " + modeLabel(mode), lore,
                s -> cycle(s, setting, "Bot flood mode is now " + plainMode(settings.next(setting))),
                mode.equals("mitigate"));
    }

    public MenuPage packetChecks() {
        return session -> {
            List<MenuItem> items = new ArrayList<>();
            Map<String, Long> counts = engine.ledger().map(l -> l.countsByCheck()).orElse(Map.of());
            boolean on = "enforce".equals(settings.current(settings.find(SettingsService.GLOBAL_MODE).orElseThrow()));
            int index = 0;
            for (PacketCheck check : PacketCheck.values()) {
                Setting setting = settings.find(check.id()).orElseThrow();
                String current = settings.current(setting);
                Icon icon = current.equals("disabled") ? Icon.CHECK_DISABLED : iconFor(check);
                Friendly f = FRIENDLY.get(check);
                List<String> lore = new ArrayList<>();
                lore.add("&7" + f.line1());
                lore.add("&7" + f.line2());
                lore.add("");
                lore.add("&7Now: " + modeLabel(current) + " &8- " + modeMeaning(current));
                lore.add("&7Caught so far: &f" + counts.getOrDefault(check.id(), 0L));
                if (current.equals("mitigate") && !on) {
                    lore.add("&6Will only block once Protection is ON.");
                }
                lore.add("");
                lore.add("&eClick to change: &8Off > &aWatch &8> &cBlock");
                lore.add("&8" + check.id());
                int slot = centredSlot(index++, PacketCheck.values().length);
                items.add(new MenuItem(slot, icon, "&f" + f.name() + " &8| " + modeLabel(current), lore,
                        s -> cycle(s, setting, f.name() + " is now " + plainMode(settings.next(setting))),
                        current.equals("mitigate")));
            }
            items.add(new MenuItem(27, Icon.INFO, "&fWhat do the modes mean?",
                    List.of("&8Off&7: the check does nothing.", "&aWatch&7: logs it, never interferes.",
                            "&cBlock&7: stops the action (only while", "&7Protection is ON on the main page)."), null));
            items.add(new MenuItem(31, Icon.BACK, "&7Back", List.of(), s -> s.show(main())));
            return new Menu("Black Door - Cheat checks", 4, items);
        };
    }

    /** Rows of up to 7 starting on the second row, each centred so the edge columns stay empty. */
    private static int centredSlot(int index, int total) {
        int row = index / 7;
        int inRow = Math.min(7, total - row * 7);
        return 9 * (row + 1) + (9 - inRow) / 2 + index % 7;
    }

    private void cycle(MenuSession session, Setting setting, String successMessage) {
        if (!session.viewer().hasPermission(SettingsService.EDIT_PERMISSION)) {
            session.viewer().sendMessage(Text.PREFIX + "&cYou can look, but you are not allowed to change settings ("
                    + SettingsService.EDIT_PERMISSION + ").");
            return;
        }
        String next = settings.next(setting);
        String actor = session.viewer().name();
        boolean queued = settings.change(setting, next, actor, result -> {
            report(session, successMessage, result);
            session.refresh();
        });
        if (!queued) {
            session.viewer().sendMessage(Text.PREFIX + "&cBlack Door is busy; try again in a moment.");
        }
    }

    private static void report(MenuSession session, String successMessage, ReloadResult result) {
        if (result.success()) {
            session.viewer().sendMessage(Text.PREFIX + "&a" + successMessage + ".");
        } else {
            session.viewer().sendMessage(Text.PREFIX + "&cThat change was not saved: " + result.errors().stream()
                    .findFirst().map(ConfigError::toString).map(Text::escape).orElse("unknown error"));
        }
    }

    private void reload(MenuSession session) {
        if (!session.viewer().hasPermission("blackdoor.command.reload")) {
            session.viewer().sendMessage(Text.PREFIX + "&cYou are not allowed to reload settings.");
            return;
        }
        String actor = session.viewer().name();
        boolean queued = engine.submitWork(() -> {
            ReloadResult result = engine.reloadConfiguration(actor);
            session.viewer().sendMessage(Text.PREFIX + (result.success()
                    ? "&aSettings reloaded." : "&cThe config files have a mistake; old settings kept. See the server log."));
            session.refresh();
        });
        if (!queued) {
            session.viewer().sendMessage(Text.PREFIX + "&cBlack Door is busy; try again in a moment.");
        }
    }

    private List<String> statusLore() {
        List<String> lore = new ArrayList<>();
        lore.add("&7Black Door is " + switch (engine.state()) {
            case RUNNING -> "&arunning normally";
            case DEGRADED -> "&erunning with problems";
            default -> "&c" + engine.state().name().toLowerCase(java.util.Locale.ROOT);
        });
        engine.ledger().ifPresent(l -> lore.add("&7Suspicious actions logged: &f" + l.total()));
        engine.packetEngine().ifPresent(p -> {
            PacketSecurityEngine.Stats stats = p.stats();
            lore.add("&7Players being watched: &f" + stats.sessions());
            lore.add("&7Actions checked: &f" + stats.processed());
            lore.add("&7Actions blocked: &f" + stats.dropped());
            long unreadable = engine.platform().protocol().translationErrors();
            lore.add("&7Unreadable packets: " + (unreadable == 0 ? "&f0" : "&c" + unreadable + " &7(not inspected)"));
        });
        engine.connectionGuard().ifPresent(g -> {
            var attack = g.attackStatus();
            lore.add("&7Bot flood mode: " + (attack.active() ? "&cACTIVE" : "&fidle")
                    + (g.warmingUp() ? " &6(warming up)" : ""));
            lore.add("&7Known addresses: &f" + attack.knownAddresses() + " &8| &f" + attack.refused() + " &7refused");
        });
        for (String reason : engine.degradedReasons()) {
            lore.add("&e! " + Text.escape(reason));
        }
        return lore;
    }

    private static MenuItem locked(int slot, Icon icon, String name, String what, String phase) {
        return new MenuItem(slot, icon, "&8" + name + " &7(coming soon)",
                List.of("&7" + what, "", "&8Not available yet - planned for " + phase + "."), null);
    }

    static String plainMode(String mode) {
        return switch (mode) {
            case "disabled" -> "Off";
            case "mitigate" -> "Block";
            default -> "Watch";
        };
    }

    private static String modeLabel(String mode) {
        return switch (mode) {
            case "disabled" -> "&8Off";
            case "mitigate" -> "&cBlock";
            default -> "&aWatch";
        };
    }

    private static String modeMeaning(String mode) {
        return switch (mode) {
            case "disabled" -> "&7not checking";
            case "mitigate" -> "&7stops it";
            default -> "&7logs it only";
        };
    }
}
