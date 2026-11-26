package io.github.intruder_tech.blackdoor.proxy.bungee;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.common.log.JulLogger;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.platform.PlatformInfo;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import io.github.intruder_tech.blackdoor.protocol.UnavailableProtocolBridge;
import net.md_5.bungee.api.plugin.Plugin;

/** BungeeCord entry point: lifecycle, administration commands and join/connection limits. */
public final class BlackDoorBungeePlugin extends Plugin {

    private BlackDoorEngine engine;

    @Override
    public void onEnable() {
        BdLogger logger = new JulLogger(getLogger());
        try {
            PlatformInfo info = new PlatformInfo(PlatformType.BUNGEECORD, getProxy().getName(), getProxy().getVersion(), null);
            engine = new BlackDoorEngine(new BungeePlatform(info, new BungeeScheduler(this), logger,
                    getDataFolder().toPath(), new UnavailableProtocolBridge("not used on proxies in Phase 1"),
                    getDescription().getVersion()), TimeSource.system());
            engine.enable();
            getProxy().getPluginManager().registerCommand(this, new BungeeCommand(engine.commands()));
            engine.proxyGate().ifPresent(gate ->
                    getProxy().getPluginManager().registerListener(this, new BungeeGuardListener(gate)));
        } catch (RuntimeException e) {
            logger.error("Black Door could not start on BungeeCord.", e);
            engine = null;
        }
    }

    @Override
    public void onDisable() {
        getProxy().getPluginManager().unregisterCommands(this);
        getProxy().getPluginManager().unregisterListeners(this);
        if (engine != null) {
            engine.disable();
            engine = null;
        }
    }
}
