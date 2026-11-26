package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.config.ConfigReader;

/** Parsed {@code enforcement.yml}. Immutable. */
public record EnforcementSettings(Mode mode, int findingsToKick, int windowSeconds) {

    public enum Mode {
        DISABLED,
        /** Log who would have been kicked; touch nobody. */
        WATCH,
        KICK
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
                r.getInt("window-seconds", d.windowSeconds(), 5, 3600));
    }
}
