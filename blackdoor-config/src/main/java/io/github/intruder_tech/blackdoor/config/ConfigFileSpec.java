package io.github.intruder_tech.blackdoor.config;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Describes one configuration file: its name, current schema version, bundled defaults,
 * migrations, and the parser that turns a validated tree into an immutable settings object.
 *
 * @param <T> immutable settings type produced by the parser
 */
public record ConfigFileSpec<T>(String fileName, int currentVersion, String defaultResource,
                                List<ConfigMigration> migrations, Function<ConfigReader, T> parser) {

    /** Key holding the schema version in every file. */
    public static final String VERSION_KEY = "config-version";

    /** Plain file names only: no directories, no traversal (SR-121). */
    private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,40}[.]yml");

    public ConfigFileSpec {
        Objects.requireNonNull(fileName, "fileName");
        if (!FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("invalid config file name: " + fileName);
        }
        if (currentVersion < 1) {
            throw new IllegalArgumentException("currentVersion must be >= 1");
        }
        Objects.requireNonNull(defaultResource, "defaultResource");
        migrations = List.copyOf(migrations);
        Objects.requireNonNull(parser, "parser");
        for (ConfigMigration m : migrations) {
            if (m.fromVersion() < 1 || m.fromVersion() >= currentVersion) {
                throw new IllegalArgumentException("migration from version " + m.fromVersion() + " is out of range in " + fileName);
            }
            if (migrations.stream().filter(o -> o.fromVersion() == m.fromVersion()).count() > 1) {
                throw new IllegalArgumentException("duplicate migration from version " + m.fromVersion() + " in " + fileName);
            }
        }
    }

    ConfigMigration migrationFrom(int version) {
        for (ConfigMigration m : migrations) {
            if (m.fromVersion() == version) {
                return m;
            }
        }
        return null;
    }
}
