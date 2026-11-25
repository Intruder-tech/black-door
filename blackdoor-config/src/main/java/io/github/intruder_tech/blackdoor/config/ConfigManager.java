package io.github.intruder_tech.blackdoor.config;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the active {@link ConfigSnapshot}. Loading is all-or-nothing across every registered file:
 * either every file is valid and the new snapshot replaces the old one atomically, or nothing
 * changes (SR-122). If no valid configuration has ever been loaded, the bundled defaults are used
 * so the plugin still starts in its secure default state.
 *
 * <p>Blocking file I/O: call {@link #load()} from startup or a worker thread, never from a game
 * or network thread.
 */
public final class ConfigManager {

    private final Path directory;
    private final List<ConfigFileSpec<?>> specs;
    private final ClassLoader resources;
    private final TimeSource time;
    private final AtomicReference<ConfigSnapshot> current = new AtomicReference<>();

    public ConfigManager(Path directory, List<ConfigFileSpec<?>> specs, ClassLoader resources, TimeSource time) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.specs = List.copyOf(specs);
        this.resources = Objects.requireNonNull(resources, "resources");
        this.time = Objects.requireNonNull(time, "time");
        Set<String> names = new HashSet<>();
        for (ConfigFileSpec<?> spec : this.specs) {
            if (!names.add(spec.fileName())) {
                throw new IllegalArgumentException("duplicate config file " + spec.fileName());
            }
        }
    }

    /** Loads (or reloads) every file. Serialized: concurrent callers wait for each other. */
    public synchronized ReloadResult load() {
        ConfigFileLoader loader = new ConfigFileLoader(directory, resources, time.currentTimeMillis());
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> fingerprints = new LinkedHashMap<>();
        List<ConfigError> errors = new ArrayList<>();
        List<ConfigError> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (ConfigFileSpec<?> spec : specs) {
            try {
                ConfigFileLoader.Loaded<?> loaded = loader.load(spec);
                values.put(spec.fileName(), loaded.value());
                fingerprints.put(spec.fileName(), loaded.fingerprint());
                warnings.addAll(loaded.warnings());
                notes.addAll(loaded.notes());
            } catch (ConfigLoadException e) {
                errors.addAll(e.errors());
            } catch (IllegalArgumentException e) {
                errors.add(ConfigError.error(spec.fileName(), "", e.getMessage()));
            }
        }

        ConfigSnapshot previous = current.get();
        if (errors.isEmpty()) {
            ConfigSnapshot next = new ConfigSnapshot(values, fingerprints, time.currentTimeMillis(), false);
            current.set(next);
            return new ReloadResult(true, false, errors, warnings, changedFiles(previous, next), notes);
        }
        if (previous != null) {
            notes.add("previous configuration kept");
            return new ReloadResult(false, previous.defaultsOnly(), errors, warnings, Set.of(), notes);
        }
        current.set(defaultsSnapshot(loader, errors));
        notes.add("using bundled defaults until the configuration is fixed");
        return new ReloadResult(false, true, errors, warnings, Set.of(), notes);
    }

    /** The active snapshot. */
    public ConfigSnapshot current() {
        ConfigSnapshot snapshot = current.get();
        if (snapshot == null) {
            throw new IllegalStateException("configuration not loaded yet");
        }
        return snapshot;
    }

    public Path directory() {
        return directory;
    }

    private ConfigSnapshot defaultsSnapshot(ConfigFileLoader loader, List<ConfigError> errors) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> fingerprints = new LinkedHashMap<>();
        for (ConfigFileSpec<?> spec : specs) {
            try {
                values.put(spec.fileName(), loader.loadDefaults(spec));
                fingerprints.put(spec.fileName(), "defaults");
            } catch (ConfigLoadException e) {
                // A broken bundled default is a build defect; surface it loudly.
                errors.addAll(e.errors());
                throw new IllegalStateException("bundled default for " + spec.fileName() + " is invalid: " + e.getMessage());
            }
        }
        return new ConfigSnapshot(values, fingerprints, time.currentTimeMillis(), true);
    }

    private static Set<String> changedFiles(ConfigSnapshot previous, ConfigSnapshot next) {
        if (previous == null) {
            return next.fingerprints().keySet();
        }
        Set<String> changed = new HashSet<>();
        next.fingerprints().forEach((file, hash) -> {
            if (!hash.equals(previous.fingerprints().get(file))) {
                changed.add(file);
            }
        });
        return changed;
    }
}
