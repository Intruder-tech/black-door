package io.github.intruder_tech.blackdoor.core.config;

import io.github.intruder_tech.blackdoor.config.ConfigReader;

import java.util.regex.Pattern;

/** Parsed {@code config.yml}. Immutable. */
public record GeneralSettings(Profile profile, Mode mode, String language, int workerThreads, int workerQueueCapacity,
                              int reloadCooldownSeconds, boolean auditEnabled, boolean setupCompleted) {

    /** Deployment profile (docs/CONFIGURATION.md section 3). Phase 1: recorded only. */
    public enum Profile { SURVIVAL, SMP, COMPETITIVE_PVP, MINIGAMES, CREATIVE, CUSTOM, PROXY_NETWORK }

    /** Global mode. Monitor is the default and the only meaningful value until enforcement exists. */
    public enum Mode { MONITOR, ENFORCE }

    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2}_[A-Z]{2}");

    public static GeneralSettings parse(ConfigReader r) {
        return new GeneralSettings(
                r.getEnum("profile", Profile.class, Profile.SURVIVAL),
                r.getEnum("mode", Mode.class, Mode.MONITOR),
                r.getString("language", "en_US", 5, LANGUAGE),
                r.getInt("workers.threads", 1, 1, 4),
                r.getInt("workers.queue-capacity", 1024, 64, 65536),
                r.getInt("commands.reload-cooldown-seconds", 5, 0, 3600),
                r.getBoolean("audit.enabled", true),
                r.getBoolean("setup.completed", false));
    }
}
