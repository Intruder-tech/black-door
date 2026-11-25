package io.github.intruder_tech.blackdoor.config;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Immutable set of all parsed configuration files. Swapped atomically on reload; readers never see
 * a partially applied configuration (SR-122).
 */
public final class ConfigSnapshot {

    private final Map<String, Object> values;
    private final Map<String, String> fingerprints;
    private final long loadedAtMillis;
    private final boolean defaultsOnly;

    ConfigSnapshot(Map<String, Object> values, Map<String, String> fingerprints, long loadedAtMillis, boolean defaultsOnly) {
        this.values = Map.copyOf(values);
        this.fingerprints = Map.copyOf(fingerprints);
        this.loadedAtMillis = loadedAtMillis;
        this.defaultsOnly = defaultsOnly;
    }

    /** Settings parsed from the given file. */
    public <T> T get(ConfigFileSpec<T> spec) {
        Object value = values.get(spec.fileName());
        if (value == null) {
            throw new NoSuchElementException("no configuration loaded for " + spec.fileName());
        }
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        return typed;
    }

    /** SHA-256 (hex) of each file as loaded; {@code "defaults"} when bundled defaults are in use. */
    public Map<String, String> fingerprints() {
        return fingerprints;
    }

    public long loadedAtMillis() {
        return loadedAtMillis;
    }

    /** True when the files on disk could not be used and bundled defaults are active. */
    public boolean defaultsOnly() {
        return defaultsOnly;
    }
}
