package io.github.intruder_tech.blackdoor.config;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Typed, validating reads from a parsed YAML tree. Every problem is collected rather than thrown,
 * so an operator sees all mistakes at once. Missing keys fall back to the given default; keys that
 * are present but wrong are errors; keys that are never read are reported as warnings (typos).
 */
public final class ConfigReader {

    private final String file;
    private final Map<String, Object> root;
    private final List<ConfigError> problems = new ArrayList<>();
    private final Set<String> knownPaths = new HashSet<>();

    public ConfigReader(String file, Map<String, Object> root) {
        this.file = Objects.requireNonNull(file, "file");
        this.root = Objects.requireNonNull(root, "root");
    }

    public String file() {
        return file;
    }

    public int getInt(String path, int defaultValue, int min, int max) {
        return (int) getLong(path, defaultValue, min, max);
    }

    public long getLong(String path, long defaultValue, long min, long max) {
        Object raw = lookup(path);
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Integer || raw instanceof Long || raw instanceof BigInteger)) {
            return invalid(path, "must be a whole number, got " + describe(raw), defaultValue);
        }
        BigInteger value = new BigInteger(raw.toString());
        if (value.compareTo(BigInteger.valueOf(min)) < 0 || value.compareTo(BigInteger.valueOf(max)) > 0) {
            return invalid(path, "must be between " + min + " and " + max + ", got " + value, defaultValue);
        }
        return value.longValue();
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        Object raw = lookup(path);
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Boolean value)) {
            return invalid(path, "must be true or false, got " + describe(raw), defaultValue);
        }
        return value;
    }

    public String getString(String path, String defaultValue, int maxLength, Pattern allowed) {
        Object raw = lookup(path);
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof String value)) {
            return invalid(path, "must be text, got " + describe(raw), defaultValue);
        }
        if (value.length() > maxLength) {
            return invalid(path, "must be at most " + maxLength + " characters", defaultValue);
        }
        if (allowed != null && !allowed.matcher(value).matches()) {
            return invalid(path, "has an invalid format (expected " + allowed.pattern() + ")", defaultValue);
        }
        return value;
    }

    /** Reads an enum; accepts {@code competitive-pvp} style values for {@code COMPETITIVE_PVP}. */
    public <E extends Enum<E>> E getEnum(String path, Class<E> type, E defaultValue) {
        Object raw = lookup(path);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof String text) {
            String normalized = text.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equals(normalized)) {
                    return constant;
                }
            }
        }
        List<String> options = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            options.add(constant.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return invalid(path, "must be one of " + options + ", got " + describe(raw), defaultValue);
    }

    /** Marks a path as known without reading it (for keys handled elsewhere). */
    public void markKnown(String path) {
        knownPaths.add(path);
    }

    /** Adds a custom validation error. */
    public void error(String path, String message) {
        problems.add(ConfigError.error(file, path, message));
    }

    /** Adds warnings for every key that was never read. Call after parsing. */
    public void reportUnknownKeys() {
        collectUnknown("", root);
    }

    public List<ConfigError> errors() {
        return problems.stream().filter(p -> p.severity() == ConfigError.Severity.ERROR).toList();
    }

    public List<ConfigError> warnings() {
        return problems.stream().filter(p -> p.severity() == ConfigError.Severity.WARNING).toList();
    }

    private void collectUnknown(String prefix, Map<String, Object> node) {
        for (Map.Entry<String, Object> e : node.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (knownPaths.contains(path)) {
                continue;
            }
            if (e.getValue() instanceof Map<?, ?> nested && hasKnownChild(path)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) nested;
                collectUnknown(path, child);
            } else {
                problems.add(ConfigError.warning(file, path, "unknown key (ignored); check spelling"));
            }
        }
    }

    private boolean hasKnownChild(String path) {
        String prefix = path + ".";
        for (String known : knownPaths) {
            if (known.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Object lookup(String path) {
        knownPaths.add(path);
        Object current = root;
        for (String part : path.split("[.]")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private <T> T invalid(String path, String message, T defaultValue) {
        problems.add(ConfigError.error(file, path, message));
        return defaultValue;
    }

    private static String describe(Object raw) {
        if (raw instanceof Map<?, ?>) {
            return "a section";
        }
        if (raw instanceof List<?>) {
            return "a list";
        }
        String text = String.valueOf(raw);
        return "'" + (text.length() > 40 ? text.substring(0, 40) + "..." : text) + "'";
    }
}
