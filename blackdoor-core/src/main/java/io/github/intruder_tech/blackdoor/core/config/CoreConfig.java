package io.github.intruder_tech.blackdoor.core.config;

import io.github.intruder_tech.blackdoor.config.ConfigFileSpec;
import io.github.intruder_tech.blackdoor.packets.PacketSettings;

import java.util.List;

/** Configuration files owned by core. Later phases add network.yml, exploits.yml, etc. */
public final class CoreConfig {

    public static final ConfigFileSpec<GeneralSettings> GENERAL = new ConfigFileSpec<>(
            "config.yml", 1, "blackdoor-defaults/config.yml", List.of(), GeneralSettings::parse);

    public static final ConfigFileSpec<PacketSettings> CHECKS = new ConfigFileSpec<>(
            "checks.yml", 1, "blackdoor-defaults/checks.yml", List.of(), PacketSettings::parse);

    public static final List<ConfigFileSpec<?>> ALL = List.of(GENERAL, CHECKS);

    private CoreConfig() {
    }
}
