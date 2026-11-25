package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.common.log.JulLogger;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.platform.PlatformInfo;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;
import io.github.intruder_tech.blackdoor.protocol.UnavailableProtocolBridge;
import io.github.intruder_tech.blackdoor.protocol.packetevents.PacketEventsBridge;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for every Bukkit-family server (Bukkit, Spigot, Paper, Purpur, Folia), 1.8.8 and newer.
 * Chooses the Paper/Folia scheduler at runtime when region schedulers exist.
 */
public final class BlackDoorBukkitPlugin extends JavaPlugin {

    private static final String PAPER_SCHEDULER = "io.github.intruder_tech.blackdoor.platform.paper.PaperPlatformScheduler";
    private static final String PACKETEVENTS_PLUGIN = "packetevents";

    private BlackDoorEngine engine;

    @Override
    public void onEnable() {
        BdLogger logger = new JulLogger(getLogger());
        ProtocolBridge protocol = getServer().getPluginManager().isPluginEnabled(PACKETEVENTS_PLUGIN)
                ? new PacketEventsBridge()
                : new UnavailableProtocolBridge("PacketEvents plugin not installed or not enabled");
        try {
            PlatformInfo info = BukkitEnvironment.detect(getServer(), protocol);
            PlatformScheduler scheduler = createScheduler(logger, info);
            BukkitMenuPresenter presenter = new BukkitMenuPresenter(scheduler, logger);
            engine = new BlackDoorEngine(new BukkitPlatform(info, scheduler, logger, getDataFolder().toPath(),
                    protocol, getDescription().getVersion(), presenter), TimeSource.system());
            engine.enable();
            getServer().getPluginManager().registerEvents(presenter, this);
            registerCommand(scheduler);
        } catch (RuntimeException e) {
            logger.error("Black Door could not start and will disable itself.", e);
            engine = null;
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.disable();
            engine = null;
        }
    }

    private void registerCommand(PlatformScheduler scheduler) {
        PluginCommand command = getCommand(BlackDoorEngine.BACKEND_LABEL);
        if (command == null) {
            getLogger().severe("Command '" + BlackDoorEngine.BACKEND_LABEL + "' missing from plugin.yml");
            return;
        }
        BukkitCommandBridge bridge = new BukkitCommandBridge(engine.commands(), scheduler);
        command.setExecutor(bridge);
        command.setTabCompleter(bridge);
    }

    /**
     * Paper 1.20+ and Folia: region-aware scheduler from the Paper adapter (bundled in the same jar).
     * Loaded by a constant class name so this class never links against Paper-only API.
     */
    private PlatformScheduler createScheduler(BdLogger logger, PlatformInfo info) {
        if (BukkitEnvironment.hasRegionSchedulers()) {
            try {
                return (PlatformScheduler) Class.forName(PAPER_SCHEDULER)
                        .getConstructor(Plugin.class)
                        .newInstance(this);
            } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
                if (info.type() == PlatformType.FOLIA) {
                    throw new IllegalStateException("Paper adapter failed to load on Folia; refusing to start", e);
                }
                logger.error("Region schedulers detected but the Paper adapter failed to load; "
                        + "falling back to the Bukkit scheduler.", e);
            }
        }
        return new BukkitPlatformScheduler(this);
    }
}
