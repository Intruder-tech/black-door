package io.github.intruder_tech.blackdoor.proxy.bungee;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.core.platform.Platform;
import io.github.intruder_tech.blackdoor.core.platform.PlatformInfo;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;

import java.nio.file.Path;

record BungeePlatform(PlatformInfo info, PlatformScheduler scheduler, BdLogger logger, Path dataDirectory,
                      ProtocolBridge protocol, String pluginVersion) implements Platform {
}
