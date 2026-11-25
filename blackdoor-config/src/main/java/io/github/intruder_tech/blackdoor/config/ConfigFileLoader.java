package io.github.intruder_tech.blackdoor.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads, migrates and validates one configuration file. Blocking I/O: never call on game threads. */
final class ConfigFileLoader {

    private final Path directory;
    private final ClassLoader resources;
    private final long nowMillis;

    ConfigFileLoader(Path directory, ClassLoader resources, long nowMillis) {
        this.directory = directory.toAbsolutePath().normalize();
        this.resources = Objects.requireNonNull(resources, "resources");
        this.nowMillis = nowMillis;
    }

    record Loaded<T>(T value, String fingerprint, List<ConfigError> warnings, List<String> notes) {
    }

    <T> Loaded<T> load(ConfigFileSpec<T> spec) throws ConfigLoadException {
        Path file = resolve(spec.fileName());
        List<String> notes = new ArrayList<>();
        try {
            Files.createDirectories(directory);
            if (Files.notExists(file)) {
                writeAtomically(file, readDefault(spec));
                notes.add("created default " + spec.fileName());
            }
        } catch (IOException e) {
            throw new ConfigLoadException(ConfigError.error(spec.fileName(), "", "cannot create file: " + e.getClass().getSimpleName()));
        }
        Map<String, Object> root = YamlLoader.load(file);
        int version = readVersion(spec, root);
        if (version < spec.currentVersion()) {
            migrate(spec, file, root, version);
            notes.add("migrated " + spec.fileName() + " from version " + version + " to " + spec.currentVersion()
                    + " (backup kept)");
        }
        ConfigReader reader = new ConfigReader(spec.fileName(), root);
        T value = parse(spec, reader);
        reader.reportUnknownKeys();
        return new Loaded<>(value, fingerprint(file, spec.fileName()), reader.warnings(), notes);
    }

    /** Parses only the bundled default resource; used when no valid file has ever been loaded. */
    <T> T loadDefaults(ConfigFileSpec<T> spec) throws ConfigLoadException {
        String text = new String(readDefault(spec), StandardCharsets.UTF_8);
        Map<String, Object> root = YamlLoader.parse(spec.fileName() + " (bundled default)", text);
        return parse(spec, new ConfigReader(spec.fileName(), root));
    }

    private static <T> T parse(ConfigFileSpec<T> spec, ConfigReader reader) throws ConfigLoadException {
        reader.markKnown(ConfigFileSpec.VERSION_KEY);
        T value;
        try {
            value = spec.parser().apply(reader);
        } catch (RuntimeException e) {
            throw new ConfigLoadException(ConfigError.error(spec.fileName(), "", "parser failed: " + e.getClass().getSimpleName()));
        }
        if (!reader.errors().isEmpty()) {
            throw new ConfigLoadException(reader.errors());
        }
        return value;
    }

    private static int readVersion(ConfigFileSpec<?> spec, Map<String, Object> root) throws ConfigLoadException {
        Object raw = root.get(ConfigFileSpec.VERSION_KEY);
        if (!(raw instanceof Integer version)) {
            throw new ConfigLoadException(ConfigError.error(spec.fileName(), ConfigFileSpec.VERSION_KEY,
                    "missing or not a whole number; this file needs a config-version"));
        }
        if (version < 1) {
            throw new ConfigLoadException(ConfigError.error(spec.fileName(), ConfigFileSpec.VERSION_KEY, "must be >= 1"));
        }
        if (version > spec.currentVersion()) {
            throw new ConfigLoadException(ConfigError.error(spec.fileName(), ConfigFileSpec.VERSION_KEY,
                    "version " + version + " is newer than this build supports (" + spec.currentVersion()
                            + "); refusing to downgrade"));
        }
        return version;
    }

    private void migrate(ConfigFileSpec<?> spec, Path file, Map<String, Object> root, int from) throws ConfigLoadException {
        for (int v = from; v < spec.currentVersion(); v++) {
            if (spec.migrationFrom(v) == null) {
                throw new ConfigLoadException(ConfigError.error(spec.fileName(), ConfigFileSpec.VERSION_KEY,
                        "no migration from version " + v));
            }
        }
        try {
            Path backup = resolve(spec.fileName() + ".bak-v" + from);
            if (Files.exists(backup)) {
                backup = resolve(spec.fileName() + ".bak-v" + from + "-" + nowMillis);
            }
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
            for (int v = from; v < spec.currentVersion(); v++) {
                spec.migrationFrom(v).migrate(root);
                root.put(ConfigFileSpec.VERSION_KEY, v + 1);
            }
            writeAtomically(file, YamlLoader.dump(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            throw new ConfigLoadException(ConfigError.error(spec.fileName(), "", "migration failed: " + e.getClass().getSimpleName()));
        }
    }

    private byte[] readDefault(ConfigFileSpec<?> spec) throws ConfigLoadException {
        try (InputStream in = resources.getResourceAsStream(spec.defaultResource())) {
            if (in == null) {
                throw new ConfigLoadException(ConfigError.error(spec.fileName(), "", "bundled default is missing from the jar"));
            }
            return in.readNBytes((int) YamlLoader.MAX_FILE_BYTES);
        } catch (IOException e) {
            throw new ConfigLoadException(ConfigError.error(spec.fileName(), "", "cannot read bundled default"));
        }
    }

    /** Resolves a name inside the config directory, refusing anything that escapes it (SR-121). */
    private Path resolve(String name) {
        Path resolved = directory.resolve(name).normalize();
        if (!directory.equals(resolved.getParent())) {
            throw new IllegalArgumentException("path escapes configuration directory: " + name);
        }
        return resolved;
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, content);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String fingerprint(Path file, String name) throws ConfigLoadException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ConfigLoadException(ConfigError.error(name, "", "cannot fingerprint file"));
        }
    }
}
