package io.github.intruder_tech.blackdoor.config;

import java.util.Map;

/**
 * Upgrades a configuration tree from {@link #fromVersion()} to {@code fromVersion() + 1}.
 * The loader updates {@code config-version} itself; a migration only reshapes keys.
 */
public interface ConfigMigration {

    int fromVersion();

    void migrate(Map<String, Object> root);
}
