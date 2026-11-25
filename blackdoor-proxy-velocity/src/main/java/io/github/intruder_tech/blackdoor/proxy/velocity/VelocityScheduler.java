package io.github.intruder_tech.blackdoor.proxy.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;

import java.util.UUID;

/** Velocity APIs are thread-safe; tasks run on Velocity's scheduler pool. */
record VelocityScheduler(Object plugin, ProxyServer server) implements PlatformScheduler {

    @Override
    public void runGlobal(Runnable task) {
        server.getScheduler().buildTask(plugin, task).schedule();
    }

    @Override
    public void runForPlayer(UUID playerId, Runnable task) {
        if (server.getPlayer(playerId).isPresent()) {
            runGlobal(task);
        }
    }

    @Override
    public void cancelAll() {
        server.getScheduler().tasksByPlugin(plugin).forEach(ScheduledTask::cancel);
    }
}
