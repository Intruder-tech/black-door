package io.github.intruder_tech.blackdoor.core.platform;

import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;

import java.util.Objects;
import java.util.Optional;

/**
 * Facts about the running platform, detected once at startup.
 *
 * @param minecraftVersion game version of a backend server; {@code null} on proxies
 */
public record PlatformInfo(PlatformType type, String implementationName, String implementationVersion,
                           MinecraftVersion minecraftVersion) {

    public PlatformInfo {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(implementationName, "implementationName");
        Objects.requireNonNull(implementationVersion, "implementationVersion");
        if (!type.isProxy()) {
            Objects.requireNonNull(minecraftVersion, "minecraftVersion is required on servers");
        }
    }

    public Optional<MinecraftVersion> minecraft() {
        return Optional.ofNullable(minecraftVersion);
    }
}
