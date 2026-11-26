package io.github.intruder_tech.blackdoor.core.settings;

import io.github.intruder_tech.blackdoor.config.ConfigFileSpec;
import io.github.intruder_tech.blackdoor.config.ReloadResult;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.config.CoreConfig;
import io.github.intruder_tech.blackdoor.core.config.GeneralSettings;
import io.github.intruder_tech.blackdoor.packets.CheckMode;
import io.github.intruder_tech.blackdoor.packets.PacketCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The settings that can be changed in game (GUI and commands). Every change goes through
 * {@link BlackDoorEngine#applyChange}: comment-preserving edit, schema validation, atomic write,
 * all-or-nothing reload, audit entry. Only values from each setting's option list are accepted.
 */
public final class SettingsService {

    /** Permission needed to change settings in game; viewing only needs the command permission. */
    public static final String EDIT_PERMISSION = "blackdoor.gui.edit";

    public static final String GLOBAL_MODE = "general.mode";
    public static final String PROFILE = "general.profile";
    public static final String NETWORK_MODE = "network.mode";
    public static final String AUTH_ENABLED = "auth.enabled";
    public static final String ADAPTIVE_MODE = "network.adaptive";
    public static final String ENFORCEMENT_MODE = "enforcement.mode";
    public static final String BAN_LENGTH = "enforcement.ban-minutes";

    /** One editable setting. Options are the exact values written to the file. */
    public record Setting(String id, String label, String description, ConfigFileSpec<?> spec, String path,
                          List<String> options) {
        public Setting {
            options = List.copyOf(options);
        }
    }

    private final BlackDoorEngine engine;
    private final List<Setting> settings;

    public SettingsService(BlackDoorEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        List<Setting> all = new ArrayList<>();
        all.add(new Setting(GLOBAL_MODE, "Global mode",
                "monitor: log only. enforce: checks set to 'mitigate' block packets.",
                CoreConfig.GENERAL, "mode", options(GeneralSettings.Mode.values())));
        all.add(new Setting(PROFILE, "Server profile", "Deployment profile (recorded; tunes checks in later phases).",
                CoreConfig.GENERAL, "profile", options(GeneralSettings.Profile.values())));
        for (PacketCheck check : PacketCheck.values()) {
            all.add(new Setting(check.id(), check.id(), check.description(), CoreConfig.CHECKS,
                    "packet." + check.configKey() + ".mode", options(CheckMode.values())));
        }
        all.add(new Setting(NETWORK_MODE, "Join flood protection", "Limits joins and pings per address.",
                CoreConfig.NETWORK, "mode", options(io.github.intruder_tech.blackdoor.network.NetworkSettings.Mode.values())));
        all.add(new Setting(ADAPTIVE_MODE, "Bot flood mode", "Keeps known players able to join during a login flood.",
                CoreConfig.NETWORK, "adaptive.mode",
                options(io.github.intruder_tech.blackdoor.network.NetworkSettings.Mode.values())));
        all.add(new Setting(AUTH_ENABLED, "Login system","Players must /signup and /signin.",
                CoreConfig.AUTH, "enabled", List.of("false", "true")));
        all.add(new Setting(ENFORCEMENT_MODE, "Auto kick", "Kicks players who keep getting blocked.",
                CoreConfig.ENFORCEMENT, "mode",
                options(io.github.intruder_tech.blackdoor.enforcement.EnforcementSettings.Mode.values())));
        all.add(new Setting(BAN_LENGTH, "Temporary ban", "Bans players who keep getting kicked.",
                CoreConfig.ENFORCEMENT, "ban-minutes", List.of("0", "15", "60", "1440")));
        this.settings = List.copyOf(all);
    }

    public List<Setting> all() {
        return settings;
    }

    public Optional<Setting> find(String id) {
        return settings.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /** Current value as written in the file (lowercase, kebab-case). */
    public String current(Setting setting) {
        if (setting.id().equals(GLOBAL_MODE)) {
            return value(engine.settings().mode());
        }
        if (setting.id().equals(PROFILE)) {
            return value(engine.settings().profile());
        }
        if (setting.id().equals(NETWORK_MODE)) {
            return value(engine.networkSettings().mode());
        }
        if (setting.id().equals(ADAPTIVE_MODE)) {
            return value(engine.networkSettings().adaptive().mode());
        }
        if (setting.id().equals(ENFORCEMENT_MODE)) {
            return value(engine.enforcementSettings().mode());
        }
        if (setting.id().equals(BAN_LENGTH)) {
            return Integer.toString(engine.enforcementSettings().banMinutes());
        }
        if (setting.id().equals(AUTH_ENABLED)) {
            return Boolean.toString(engine.authSettings().enabled());
        }
        for (PacketCheck check : PacketCheck.values()) {
            if (check.id().equals(setting.id())) {
                return value(engine.packetSettings().mode(check));
            }
        }
        throw new IllegalArgumentException("unknown setting " + setting.id());
    }

    /** The option after the current one (wraps around). */
    public String next(Setting setting) {
        int index = setting.options().indexOf(current(setting));
        return setting.options().get((index + 1) % setting.options().size());
    }

    /**
     * Queues a change on the worker pool. {@code done} runs on a worker thread.
     *
     * @return false if the value is not allowed or Black Door is too busy (nothing queued)
     */
    public boolean change(Setting setting, String value, String actor, Consumer<ReloadResult> done) {
        if (!setting.options().contains(value)) {
            return false;
        }
        return engine.submitWork(() -> done.accept(engine.applyChange(setting.spec(), setting.path(), value, actor)));
    }

    static String value(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static List<String> options(Enum<?>[] values) {
        List<String> out = new ArrayList<>();
        for (Enum<?> v : values) {
            out.add(value(v));
        }
        return out;
    }
}
