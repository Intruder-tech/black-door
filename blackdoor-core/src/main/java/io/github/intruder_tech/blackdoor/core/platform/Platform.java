package io.github.intruder_tech.blackdoor.core.platform;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;

import java.nio.file.Path;

/** Everything core needs from a platform adapter. */
public interface Platform {

    PlatformInfo info();

    PlatformScheduler scheduler();

    BdLogger logger();

    /** The plugin's own data folder. All files Black Door writes live below it. */
    Path dataDirectory();

    ProtocolBridge protocol();

    /** Version from the plugin descriptor. */
    String pluginVersion();
}
