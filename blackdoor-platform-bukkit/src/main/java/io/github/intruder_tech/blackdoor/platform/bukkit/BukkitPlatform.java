package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.core.menu.MenuPresenter;
import io.github.intruder_tech.blackdoor.core.platform.Platform;
import io.github.intruder_tech.blackdoor.core.platform.PlatformInfo;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import io.github.intruder_tech.blackdoor.core.platform.SecurityFacts;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;

import java.nio.file.Path;
import java.util.Optional;

record BukkitPlatform(PlatformInfo info, PlatformScheduler scheduler, BdLogger logger, Path dataDirectory,
                      ProtocolBridge protocol, String pluginVersion, MenuPresenter presenter) implements Platform {

    @Override
    public Optional<MenuPresenter> menus() {
        return Optional.of(presenter);
    }

    @Override
    public SecurityFacts securityFacts() {
        return BukkitEnvironment.securityFacts();
    }
}
