package io.github.intruder_tech.blackdoor.config;

import java.util.List;

/** Thrown when a configuration file cannot be loaded or validated. Carries every error found. */
public final class ConfigLoadException extends Exception {

    private final transient List<ConfigError> errors;

    public ConfigLoadException(List<ConfigError> errors) {
        super(errors.isEmpty() ? "configuration error" : errors.get(0).toString());
        this.errors = List.copyOf(errors);
    }

    public ConfigLoadException(ConfigError error) {
        this(List.of(error));
    }

    public List<ConfigError> errors() {
        return errors;
    }
}
