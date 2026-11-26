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

    /** Security-relevant server settings, for /blackdoor setup. Unknown by default. */
    default SecurityFacts securityFacts() {
        return SecurityFacts.UNKNOWN;
    }

    /**
     * Asks the server for the collision boxes of a block-state name; call on the server thread
     * only. Null when this server cannot answer.
     */
    default java.util.function.Function<String, java.util.Optional<java.util.List<
            io.github.intruder_tech.blackdoor.protocol.world.BlockShapes.Box>>> blockShapeSource() {
        return null;
    }

    /** In-game menus, where the platform supports them (Bukkit-family servers). */
    default java.util.Optional<io.github.intruder_tech.blackdoor.core.menu.MenuPresenter> menus() {
        return java.util.Optional.empty();
    }
}
