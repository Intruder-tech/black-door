package io.github.intruder_tech.blackdoor.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.ProxyGate;
import io.github.intruder_tech.blackdoor.core.platform.PlatformInfo;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import io.github.intruder_tech.blackdoor.protocol.UnavailableProtocolBridge;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;

/** Velocity entry point: lifecycle, administration commands and join/ping limits. */
@Plugin(id = "blackdoor", name = "BlackDoor", version = BuildConstants.VERSION,
        description = "Server-side security framework: join and ping flood protection.",
        authors = {"Intruder-tech"})
public final class BlackDoorVelocityPlugin {

    private final ProxyServer server;
    private final BdLogger logger;
    private final Path dataDirectory;
    private volatile BlackDoorEngine engine;
    private CommandMeta commandMeta;

    @Inject
    public BlackDoorVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = new Slf4jLogger(logger);
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            PlatformInfo info = new PlatformInfo(PlatformType.VELOCITY, server.getVersion().getName(),
                    server.getVersion().getVersion(), null);
            engine = new BlackDoorEngine(new VelocityPlatform(info, new VelocityScheduler(this, server), logger,
                    dataDirectory, new UnavailableProtocolBridge("not used on proxies in Phase 1"), BuildConstants.VERSION),
                    TimeSource.system());
            engine.enable();
            commandMeta = server.getCommandManager().metaBuilder(BlackDoorEngine.PROXY_LABEL)
                    .aliases("bdproxy").plugin(this).build();
            server.getCommandManager().register(commandMeta, new VelocityCommand(engine.commands()));
        } catch (RuntimeException e) {
            logger.error("Black Door could not start on Velocity.", e);
            engine = null;
        }
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        ProxyGate gate = gate();
        InboundConnection connection = event.getConnection();
        if (gate != null && event.getResult().isAllowed()
                && gate.login(address(connection), hostnameLength(connection), event.getUsername())) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(ProxyGate.REFUSAL_MESSAGE)));
        }
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        ProxyGate gate = gate();
        InboundConnection connection = event.getConnection();
        if (gate != null && event.getResult().isAllowed()
                && gate.ping(address(connection), hostnameLength(connection))) {
            event.setResult(ResultedEvent.GenericResult.denied());
        }
    }

    private ProxyGate gate() {
        BlackDoorEngine current = engine;
        return current == null ? null : current.proxyGate().orElse(null);
    }

    private static InetAddress address(InboundConnection connection) {
        InetSocketAddress remote = connection.getRemoteAddress();
        return remote == null ? null : remote.getAddress();
    }

    private static int hostnameLength(InboundConnection connection) {
        return connection.getRawVirtualHost().map(String::length).orElse(0);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (commandMeta != null) {
            server.getCommandManager().unregister(commandMeta);
        }
        if (engine != null) {
            engine.disable();
            engine = null;
        }
    }
}
