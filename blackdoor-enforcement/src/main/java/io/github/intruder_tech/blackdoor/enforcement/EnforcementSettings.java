package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.config.ConfigReader;

/** Parsed {@code enforcement.yml}. Immutable. */
public record EnforcementSettings(Mode mode, int findingsToKick, int windowSeconds, int banAfterKicks,
                                  int banMinutes, int kickMemoryHours, int quarantineMinutes) {

    public enum Mode {
        DISABLED,
        /** Log who would have been kicked; touch nobody. */
        WATCH,
        /** Keep the player in game, able only to move and chat. */
        QUARANTINE,
        KICK
    }

    /** Kick rules only; temporary bans stay off. */
    public EnforcementSettings(Mode mode, int findingsToKick, int windowSeconds) {
        this(mode, findingsToKick, windowSeconds, 3, 0, 24);
    }

    /** With the usual quarantine length. */
    public EnforcementSettings(Mode mode, int findingsToKick, int windowSeconds, int banAfterKicks,
                               int banMinutes, int kickMemoryHours) {
        this(mode, findingsToKick, windowSeconds, banAfterKicks, banMinutes, kickMemoryHours, 60);
    }

    /** Off until the owner switches it on. */
    public static EnforcementSettings defaults() {
        return new EnforcementSettings(Mode.DISABLED, 5, 60);
    }

    public static EnforcementSettings parse(ConfigReader r) {
        EnforcementSettings d = defaults();
        return new EnforcementSettings(
                r.getEnum("mode", Mode.class, d.mode()),
                r.getInt("findings-to-kick", d.findingsToKick(), 1, 1000),
                r.getInt("window-seconds", d.windowSeconds(), 5, 3600),
                r.getInt("ban-after-kicks", d.banAfterKicks(), 1, 100),
                r.getInt("ban-minutes", d.banMinutes(), 0, 43_200),
                r.getInt("kick-memory-hours", d.kickMemoryHours(), 1, 720),
                r.getInt("quarantine-minutes", d.quarantineMinutes(), 0, 43_200));
    }
}
