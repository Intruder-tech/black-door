package io.github.intruder_tech.blackdoor.core.platform;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;

import java.nio.file.Path;

/** Everything core needs from a platform adapter. */
public interface Platform {

    PlatformInfo info();

    PlatformScheduler scheduler();

    BdLogger logger();

    /**
     * The plugin's own data folder. All files Black Door writes live below it, except Paper's
     * anti-xray switch (see {@link #antiXrayFiles()}).
     */
    Path dataDirectory();

    ProtocolBridge protocol();

    /** Version from the plugin descriptor. */
    String pluginVersion();

    /** Security-relevant server settings, for /blackdoor setup. Unknown by default. */
    default SecurityFacts securityFacts() {
        return SecurityFacts.UNKNOWN;
    }

    /**
     * Paper's anti-xray files for the loaded worlds, so the control panel can switch it. Call on
     * the server thread; the returned object does its file work on whichever thread uses it.
     * Empty where Black Door cannot switch it.
     */
    default java.util.Optional<io.github.intruder_tech.blackdoor.core.antixray.AntiXrayFiles> antiXrayFiles() {
        return java.util.Optional.empty();
    }

    /**
     * Asks the server for the collision boxes of a block-state name; call on the server thread
     * only. Null when this server cannot answer.
     */
    default java.util.function.Function<String, java.util.Optional<java.util.List<
            io.github.intruder_tech.blackdoor.protocol.world.BlockShapes.Box>>> blockShapeSource() {
        return null;
    }

    /**
     * Disconnects an online player with a message; call on that player's thread
     * ({@link PlatformScheduler#runForPlayer}). Returns false if nobody was kicked: offline, exempt,
     * or not supported here.
     */
    default boolean kick(java.util.UUID playerId, String message) {
        return false;
    }

    /** In-game menus, where the platform supports them (Bukkit-family servers). */
    default java.util.Optional<io.github.intruder_tech.blackdoor.core.menu.MenuPresenter> menus() {
        return java.util.Optional.empty();
    }
}
