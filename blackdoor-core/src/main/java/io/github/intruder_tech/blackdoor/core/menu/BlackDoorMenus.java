package io.github.intruder_tech.blackdoor.core.menu;

import io.github.intruder_tech.blackdoor.config.ConfigError;
import io.github.intruder_tech.blackdoor.config.ReloadResult;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStatus;
import io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStrength;
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
            items.add(locked(14, Icon.LOCKED_COMBAT, "Combat checks", "Reach, kill aura...", "Phase 3"));
            items.add(new MenuItem(31, Icon.AUTO_KICK, "&fPunishments",
                    List.of("&7Auto action, quarantine and bans.", "&7Banned now: &f"
                            + engine.bans().map(b -> b.active().size()).orElse(0), "", "&eClick to open"),
                    s -> s.show(punishments())));
            items.add(antiXrayItem(16, session.viewer().hasPermission(SettingsService.EDIT_PERMISSION)));
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

    public MenuPage punishments() {
        return session -> {
            List<MenuItem> items = new ArrayList<>();
            items.add(autoKickItem(10));
            items.add(new MenuItem(12, Icon.CASES, "&fReview cases",
                    List.of("&7Who was acted on, and why.", "", "&eClick to open"), s -> s.show(cases())));
            items.add(banLengthItem(14));
            items.add(new MenuItem(16, Icon.QUARANTINE, "&fQuarantine",
                    List.of("&7Held now: &f" + engine.quarantine().map(q -> q.active().size()).orElse(0), "",
                            "&eClick to open"), s -> s.show(quarantine())));
            var banned = engine.bans().map(b -> b.active()).orElse(List.of());
            long now = engine.time().currentTimeMillis();
            for (int i = 0; i < Math.min(7, banned.size()); i++) {
                var ban = banned.get(i);
                items.add(new MenuItem(19 + i, Icon.BANNED_PLAYER, "&c" + Text.escape(ban.name()),
                        List.of("&7Banned by Black Door.", "&7Time left: &f"
                                        + io.github.intruder_tech.blackdoor.enforcement.BanBook
                                        .words(ban.untilMillis() - now), "", "&eClick to unban"),
                        s -> unban(s, ban.name())));
            }
            items.add(new MenuItem(27, Icon.INFO, "&fBanned now: " + banned.size(),
                    List.of("&7Up to 7 are shown here. All of them:", "&f/blackdoor bans"), null));
            items.add(new MenuItem(31, Icon.BACK, "&7Back", List.of(), s -> s.show(main())));
            return new Menu("Black Door - Punishments", 4, items);
        };
    }

    /** The newest cases; clicking one flips the staff verdict. */
    public MenuPage cases() {
        return session -> {
            List<MenuItem> items = new ArrayList<>();
            var newest = engine.cases().map(b -> b.newest(14)).orElse(List.of());
            for (int i = 0; i < newest.size(); i++) {
                items.add(caseItem(i < 7 ? 10 + i : 19 + i - 7, newest.get(i)));
            }
            items.add(new MenuItem(27, Icon.INFO, "&fNewest " + newest.size() + " cases",
                    List.of("&7Older ones and every detail:", "&f/blackdoor cases"), null));
            items.add(new MenuItem(31, Icon.BACK, "&7Back", List.of(), s -> s.show(punishments())));
            return new Menu("Black Door - Cases", 4, items);
        };
    }

    private MenuItem caseItem(int slot, io.github.intruder_tech.blackdoor.enforcement.CaseBook.Case found) {
        String what = switch (found.action()) {
            case WOULD_KICK -> "&eWould be acted on";
            case QUARANTINE -> "&dQuarantined";
            case KICK -> "&6Kicked";
            case BAN -> "&cBanned";
        };
        Icon icon = found.falseAlarm() ? Icon.CASE_FALSE : switch (found.action()) {
            case WOULD_KICK -> Icon.CASE_WATCH;
            case QUARANTINE -> Icon.QUARANTINE;
            case KICK -> Icon.CASE_KICK;
            case BAN -> Icon.CASE_BAN;
        };
        List<String> lore = new ArrayList<>();
        lore.add(what + " &7" + io.github.intruder_tech.blackdoor.enforcement.BanBook
                .words(engine.time().currentTimeMillis() - found.timeMillis()) + " ago");
        lore.add("");
        found.evidence().stream().limit(4).forEach(e -> lore.add("&8- &7" + friendlyName(e.checkId())));
        if (found.evidence().size() > 4) {
            lore.add("&8... " + (found.evidence().size() - 4) + " more: /blackdoor cases " + found.id());
        }
        lore.add("");
        lore.add(found.falseAlarm() ? "&eClick to mark as real"
                : "&eClick if this was a false alarm");
        if (!found.falseAlarm() && found.action()
                != io.github.intruder_tech.blackdoor.enforcement.CaseBook.Action.WOULD_KICK) {
            lore.add("&7That also lifts any ban on this player.");
        }
        return new MenuItem(slot, icon, "&f#" + found.id() + " " + Text.escape(found.name())
                + (found.falseAlarm() ? " &a| False alarm" : ""), lore, s -> {
                    if (mayEdit(s)) {
                        engine.judgeCase(found.id(), !found.falseAlarm(), s.viewer().name());
                        s.refresh();
                    }
                });
    }

    private void unban(MenuSession session, String name) {
        if (!mayEdit(session)) {
            return;
        }
        if (engine.unban(name, session.viewer().name())) {
            session.viewer().sendMessage(Text.PREFIX + "&aUnbanned " + Text.escape(name) + ".");
        }
        session.refresh();
    }

    private MenuItem banLengthItem(int slot) {
        Setting setting = settings.find(SettingsService.BAN_LENGTH).orElseThrow();
        var rules = engine.enforcementSettings();
        boolean on = rules.banMinutes() > 0;
        List<String> lore = new ArrayList<>();
        lore.add("&7Bans for a while after &f" + rules.banAfterKicks() + " &7auto kicks");
        lore.add("&7within &f" + rules.kickMemoryHours() + " &7hours.");
        lore.add("");
        lore.add("&7Now: " + (on ? "&c" : "&8") + banLength(rules.banMinutes()));
        if (on && rules.mode() != io.github.intruder_tech.blackdoor.enforcement.EnforcementSettings.Mode.KICK) {
            lore.add("&6Only bans while Auto action is on Kick.");
        }
        lore.add("");
        lore.add("&eClick to change: &8Off > 15 minutes > 1 hour > 1 day");
        String next = banLength(Integer.parseInt(settings.next(setting)));
        return new MenuItem(slot, on ? Icon.TEMP_BAN : Icon.CHECK_DISABLED,
                "&fTemporary ban &8| " + (on ? "&c" : "&8") + banLength(rules.banMinutes()), lore,
                s -> cycle(s, setting, "Temporary ban is now " + next), on);
    }

    private static String banLength(int minutes) {
        return minutes == 0 ? "Off" : io.github.intruder_tech.blackdoor.enforcement.BanBook.words(minutes * 60_000L);
    }

    private MenuItem autoKickItem(int slot) {
        Setting setting = settings.find(SettingsService.ENFORCEMENT_MODE).orElseThrow();
        String mode = settings.current(setting);
        var rules = engine.enforcementSettings();
        List<String> lore = new ArrayList<>();
        lore.add("&7What happens after &f" + rules.findingsToKick() + " &7blocked");
        lore.add("&7actions in &f" + rules.windowSeconds() + " &7seconds.");
        lore.add("&7Spam-style guesses never count.");
        lore.add("");
        lore.add("&7Now: " + actionColour(mode) + actionName(mode) + " &8- " + switch (mode) {
            case "kick" -> "&7kicks";
            case "quarantine" -> "&7player may only move and chat";
            case "watch" -> "&7only logs what would happen";
            default -> "&7nothing happens";
        });
        boolean acts = mode.equals("kick") || mode.equals("quarantine");
        boolean on = "enforce".equals(settings.current(settings.find(SettingsService.GLOBAL_MODE).orElseThrow()));
        if (acts && !on) {
            lore.add("&6Will only act once Protection is ON.");
        }
        lore.add("");
        lore.add("&eClick to change: &8Off > &aWatch &8> &dQuarantine &8> &cKick");
        String next = actionName(settings.next(setting));
        return new MenuItem(slot, mode.equals("disabled") ? Icon.CHECK_DISABLED : Icon.AUTO_KICK,
                "&fAuto action &8| " + actionColour(mode) + actionName(mode), lore,
                s -> cycle(s, setting, "Auto action is now " + next), acts);
    }

    private static String actionName(String mode) {
        return switch (mode) {
            case "kick" -> "Kick";
            case "quarantine" -> "Quarantine";
            case "watch" -> "Watch";
            default -> "Off";
        };
    }

    private static String actionColour(String mode) {
        return switch (mode) {
            case "kick" -> "&c";
            case "quarantine" -> "&d";
            case "watch" -> "&a";
            default -> "&8";
        };
    }

    /** Who is held, and for how long new ones are; clicking a player releases them. */
    public MenuPage quarantine() {
        return session -> {
            List<MenuItem> items = new ArrayList<>();
            Setting length = settings.find(SettingsService.QUARANTINE_LENGTH).orElseThrow();
            int minutes = engine.enforcementSettings().quarantineMinutes();
            String next = quarantineLength(Integer.parseInt(settings.next(length)));
            items.add(new MenuItem(4, Icon.TEMP_BAN, "&fQuarantine lasts &8| &d" + quarantineLength(minutes),
                    List.of("&7Applies to players held from now on.", "",
                            "&eClick to change: &815 minutes > 1 hour > 1 day > until released"),
                    s -> cycle(s, length, "Quarantine now lasts " + next)));
            var held = engine.quarantine().map(q -> q.active()).orElse(List.of());
            long now = engine.time().currentTimeMillis();
            for (int i = 0; i < Math.min(14, held.size()); i++) {
                var player = held.get(i);
                items.add(new MenuItem(i < 7 ? 10 + i : 19 + i - 7, Icon.BANNED_PLAYER,
                        "&d" + Text.escape(player.name()),
                        List.of("&7In quarantine.", "&7Ends: &f" + (player.untilMillis() == 0 ? "when staff release"
                                : "in " + io.github.intruder_tech.blackdoor.enforcement.BanBook
                                        .words(player.untilMillis() - now)), "", "&eClick to release"),
                        s -> {
                            if (mayEdit(s)) {
                                engine.release(player.name(), s.viewer().name());
                                s.refresh();
                            }
                        }));
            }
            items.add(new MenuItem(27, Icon.INFO, "&fHeld now: " + held.size(),
                    List.of("&7Hold someone yourself:", "&f/blackdoor quarantine <player>"), null));
            items.add(new MenuItem(31, Icon.BACK, "&7Back", List.of(), s -> s.show(punishments())));
            return new Menu("Black Door - Quarantine", 4, items);
        };
    }

    private static String quarantineLength(int minutes) {
        return minutes == 0 ? "until released"
                : io.github.intruder_tech.blackdoor.enforcement.BanBook.words(minutes * 60_000L);
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
        if (!mayEdit(session)) {
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

    /**
     * Paper hides the ores; Black Door switches that on or off in Paper's files. The files are
     * what is "configured"; what the server runs is what they said when it started.
     */
    private MenuItem antiXrayItem(int slot, boolean mayChange) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Hides ores from X-ray cheats.");
        lore.add("&7This is Paper's own anti-xray.");
        lore.add("");
        lore.add("&aNormal&7: hides only ores buried in rock.");
        lore.add("&7Ores touching air stay visible.");
        lore.add("&bStrong&7: also fills the rock with fake ores,");
        lore.add("&7so X-ray packs show a jumble. Costs some");
        lore.add("&7bandwidth and client performance.");
        lore.add("&7Neither hides anything above the");
        lore.add("&7configured max height.");
        lore.add("");
        var files = engine.antiXrayFiles();
        var found = files.flatMap(engine::antiXrayStatus);
        if (found.isEmpty()) {
            lore.add(files.isEmpty() ? "&8Not available on this server (Paper only)."
                    : "&ePaper's anti-xray files could not be read.");
            return new MenuItem(slot, Icon.ANTI_XRAY, "&fAnti X-Ray &8| &8Unknown", lore, null);
        }
        var status = found.get();
        AntiXrayStrength level = status.level();
        boolean on = level != AntiXrayStrength.OFF;
        boolean pending = status.pending();
        lore.add("&7Configured: " + antiXrayState(status.configured()));
        addRunning(lore, status);
        if (pending) {
            lore.add("&6Applies after the next server restart.");
        } else {
            lore.add("&8A change applies after the next server restart.");
        }
        String name = "&fAnti X-Ray &8| " + strengthColour(level) + level.label()
                + (pending ? " &6(after restart)" : "");
        lore.add("");
        lore.add(mayChange ? "&eClick to change: &8Off > &aNormal > &bStrong" : "&8You can look, but not change this.");
        return new MenuItem(slot, on ? Icon.ANTI_XRAY : Icon.CHECK_DISABLED, name, lore,
                s -> switchAntiXray(s, level.next()), on && !pending);
    }

    /** A world loaded after the server started is never shown as on. */
    private static void addRunning(List<String> lore, AntiXrayStatus status) {
        List<String> unknown = status.runningUnknown();
        Map<String, AntiXrayStrength> known = status.runningKnown();
        if (unknown.isEmpty()) {
            lore.add("&7Running now: " + antiXrayState(known));
            return;
        }
        lore.add("&7Running now: &6Unknown until restart: &f" + Text.escape(String.join(", ", unknown)));
        if (!known.isEmpty()) {
            lore.add("&7Other worlds: " + antiXrayState(known));
        }
    }

    private static String antiXrayState(Map<String, AntiXrayStrength> worlds) {
        String colour = worlds.containsValue(AntiXrayStrength.OFF) ? "&e"
                : strengthColour(worlds.containsValue(AntiXrayStrength.NORMAL)
                        ? AntiXrayStrength.NORMAL : AntiXrayStrength.STRONG);
        return colour + Text.escape(AntiXrayStatus.describe(worlds));
    }

    private static String strengthColour(AntiXrayStrength strength) {
        return switch (strength) {
            case OFF -> "&e";
            case NORMAL -> "&a";
            case STRONG -> "&b";
        };
    }

    private void switchAntiXray(MenuSession session, AntiXrayStrength strength) {
        if (!mayEdit(session)) {
            return;
        }
        // The world list is read here, on the server thread; the file work happens on a worker.
        var files = engine.antiXrayFiles().orElse(null);
        if (files == null) {
            return;
        }
        String actor = session.viewer().name();
        String word = strength.label();
        boolean queued = engine.submitWork(() -> {
            try {
                int changed = engine.setAntiXray(files, strength, actor);
                session.viewer().sendMessage(Text.PREFIX + (changed == 0
                        ? "&7Anti X-Ray is already " + word + " in Paper's files."
                        : "&aAnti X-Ray set to " + word + " in Paper's files.")
                        + " &6It applies after the next server restart.");
            } catch (java.io.IOException e) {
                session.viewer().sendMessage(Text.PREFIX + "&cAnti X-Ray was not changed: "
                        + Text.escape(String.valueOf(e.getMessage())));
            } catch (RuntimeException e) {
                // already logged and audited by the engine
                session.viewer().sendMessage(Text.PREFIX
                        + "&cAnti X-Ray was not changed: unexpected error, see the server log.");
            } finally {
                session.refresh();
            }
        });
        if (!queued) {
            session.viewer().sendMessage(Text.PREFIX + "&cBlack Door is busy; try again in a moment.");
        }
    }

    private static boolean mayEdit(MenuSession session) {
        if (session.viewer().hasPermission(SettingsService.EDIT_PERMISSION)) {
            return true;
        }
        session.viewer().sendMessage(Text.PREFIX + "&cYou can look, but you are not allowed to change settings ("
                + SettingsService.EDIT_PERMISSION + ").");
        return false;
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
