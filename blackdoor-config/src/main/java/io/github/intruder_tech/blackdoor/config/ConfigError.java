package io.github.intruder_tech.blackdoor.config;

import java.util.Objects;

/** A single validation problem, located by file and dotted key path. */
public record ConfigError(String file, String path, String message, Severity severity) {

    public enum Severity { ERROR, WARNING }

    public ConfigError {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(severity, "severity");
    }

    public static ConfigError error(String file, String path, String message) {
        return new ConfigError(file, path, message, Severity.ERROR);
    }

    public static ConfigError warning(String file, String path, String message) {
        return new ConfigError(file, path, message, Severity.WARNING);
    }

    @Override
    public String toString() {
        return file + (path.isEmpty() ? "" : " @ " + path) + ": " + message;
    }
}
