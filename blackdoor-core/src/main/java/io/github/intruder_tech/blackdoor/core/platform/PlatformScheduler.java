package io.github.intruder_tech.blackdoor.core.platform;

import java.util.UUID;

/**
 * Runs work on the thread that owns the state it touches (SR-21). Platform world/entity APIs may
 * only be used inside tasks given to this scheduler.
 *
 * <ul>
 *   <li>Spigot / legacy Paper: everything runs on the main thread.</li>
 *   <li>Paper 1.20+ and Folia: global region scheduler and per-entity schedulers.</li>
 *   <li>Proxies: the proxy's scheduler (proxy APIs are thread-safe).</li>
 * </ul>
 *
 * Blocking I/O never goes here; it belongs on Black Door's bounded worker executor.
 */
public interface PlatformScheduler {

    /** Runs on the thread owning global server state. */
    void runGlobal(Runnable task);

    /** Runs on the thread owning the given player. Dropped silently if the player is gone. */
    void runForPlayer(UUID playerId, Runnable task);

    /** Cancels every pending task scheduled by Black Door. Called on disable. */
    void cancelAll();
}
