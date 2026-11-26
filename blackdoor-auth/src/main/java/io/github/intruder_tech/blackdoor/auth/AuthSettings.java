package io.github.intruder_tech.blackdoor.auth;

import io.github.intruder_tech.blackdoor.config.ConfigReader;

/** Parsed {@code auth.yml}. Immutable. */
public record AuthSettings(boolean enabled, int sessionHours, int loginTimeoutSeconds, int maxAttempts,
                           int lockoutMinutes, int minPasswordLength) {

    /** Off until the owner switches it on. */
    public static AuthSettings defaults() {
        return new AuthSettings(false, 24, 60, 5, 5, 8);
    }

    public static AuthSettings parse(ConfigReader r) {
        AuthSettings d = defaults();
        return new AuthSettings(
                r.getBoolean("enabled", d.enabled()),
                r.getInt("session-hours", d.sessionHours(), 0, 720),
                r.getInt("login-timeout-seconds", d.loginTimeoutSeconds(), 20, 600),
                r.getInt("max-attempts", d.maxAttempts(), 1, 20),
                r.getInt("lockout-minutes", d.lockoutMinutes(), 1, 1440),
                r.getInt("min-password-length", d.minPasswordLength(), 6, 32));
    }
}
