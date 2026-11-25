package io.github.intruder_tech.blackdoor.core;

import io.github.intruder_tech.blackdoor.api.BlackDoor;
import io.github.intruder_tech.blackdoor.api.BlackDoorProvider;
import io.github.intruder_tech.blackdoor.common.concurrent.BoundedExecutor;
import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.packets.PacketSecurityEngine;
import io.github.intruder_tech.blackdoor.packets.PacketSettings;
import io.github.intruder_tech.blackdoor.state.SessionRegistry;
import io.github.intruder_tech.blackdoor.violations.ViolationLedger;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.config.ConfigError;
import io.github.intruder_tech.blackdoor.config.ConfigManager;
import io.github.intruder_tech.blackdoor.config.ConfigSnapshot;
import io.github.intruder_tech.blackdoor.config.ReloadResult;
import io.github.intruder_tech.blackdoor.core.audit.AuditLog;
import io.github.intruder_tech.blackdoor.core.command.CommandRouter;
import io.github.intruder_tech.blackdoor.core.command.sub.ChecksCommand;
import io.github.intruder_tech.blackdoor.core.command.sub.DiagnosticsCommand;
import io.github.intruder_tech.blackdoor.core.command.sub.GuiCommand;
import io.github.intruder_tech.blackdoor.core.command.sub.PlayerCommand;
import io.github.intruder_tech.blackdoor.core.command.sub.SetupCommand;
import io.github.intruder_tech.blackdoor.core.command.sub.HelpCommand;
import io.github.intruder_tech.blackdoor.core.command.sub.ReloadCommand;
import io.github.intruder_tech.blackdoor.core.command.sub.StatusCommand;
import io.github.intruder_tech.blackdoor.core.command.sub.VersionCommand;
import io.github.intruder_tech.blackdoor.core.config.CoreConfig;
import io.github.intruder_tech.blackdoor.core.config.GeneralSettings;
import io.github.intruder_tech.blackdoor.core.platform.Platform;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.VersionSupport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Composition root. Owns lifecycle and wiring; holds no domain logic. Platform adapters create one
 * engine, call {@link #enable()} on plugin enable and {@link #disable()} on plugin disable.
 */
public final class BlackDoorEngine {

    /** Root command on backends. Proxies use a different label so they never shadow the backend command. */
    public static final String BACKEND_LABEL = "blackdoor";
    public static final String PROXY_LABEL = "blackdoorproxy";

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final Platform platform;
    private final TimeSource time;
    private final BdLogger logger;
    private final ConfigManager config;
    private final CommandRouter commands;
    private final BlackDoor api;
    private final io.github.intruder_tech.blackdoor.core.settings.SettingsService settingsService;
    private final io.github.intruder_tech.blackdoor.core.menu.BlackDoorMenus menus;
    private final io.github.intruder_tech.blackdoor.core.trace.TraceRecorder traceRecorder;

    public io.github.intruder_tech.blackdoor.core.trace.TraceRecorder traceRecorder() {
        return traceRecorder;
    }

    private volatile EngineState lifecycle = EngineState.STOPPED;
    private volatile BoundedExecutor workers;
    private volatile AuditLog audit;
    private volatile ReloadResult lastReload;
    private volatile long startedAtMillis;
    private volatile ViolationLedger ledger;
    private volatile SessionRegistry sessions;
    private volatile PacketSecurityEngine packetEngine;
    private volatile boolean packetLayerAttached;
    private volatile java.util.concurrent.ScheduledExecutorService heartbeat;

    /** How often Black Door pings each player for latency and acknowledgement tracking. */
    static final long TRANSACTION_INTERVAL_MS = 50;

    public BlackDoorEngine(Platform platform, TimeSource time) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.time = Objects.requireNonNull(time, "time");
        this.logger = platform.logger();
        this.config = new ConfigManager(platform.dataDirectory(), CoreConfig.ALL,
                BlackDoorEngine.class.getClassLoader(), time);
        String label = platform.info().type().isProxy() ? PROXY_LABEL : BACKEND_LABEL;
        this.settingsService = new io.github.intruder_tech.blackdoor.core.settings.SettingsService(this);
        this.traceRecorder = new io.github.intruder_tech.blackdoor.core.trace.TraceRecorder(this);
        this.menus = new io.github.intruder_tech.blackdoor.core.menu.BlackDoorMenus(this, settingsService);
        this.commands = new CommandRouter(label, List.of(
                new HelpCommand(this),
                new SetupCommand(this),
                new GuiCommand(this),
                new StatusCommand(this),
                new ChecksCommand(this),
                new PlayerCommand(this),
                new io.github.intruder_tech.blackdoor.core.command.sub.RecordCommand(this),
                new VersionCommand(this),
                new ReloadCommand(this),
                new DiagnosticsCommand(this)), logger);
        this.api = platform::pluginVersion;
    }

    /** Loads configuration, starts workers, registers the API. Blocking file I/O: startup only. */
    public synchronized void enable() {
        if (lifecycle != EngineState.STOPPED) {
            throw new IllegalStateException("engine is " + lifecycle);
        }
        lifecycle = EngineState.STARTING;
        startedAtMillis = time.currentTimeMillis();
        try {
            lastReload = config.load();
            report(lastReload, "Configuration");
            GeneralSettings settings = settings();
            workers = new BoundedExecutor("worker", settings.workerThreads(), settings.workerQueueCapacity(), logger);
            audit = new AuditLog(platform.dataDirectory().resolve("audit"), workers, time, logger, settings.auditEnabled());
            checkEnvironment(settings);
            startPacketLayer();
            try {
                BlackDoorProvider.register(api);
            } catch (IllegalStateException e) {
                logger.warn("Another Black Door instance is registered in the API; this instance will not replace it.");
            }
            lifecycle = EngineState.RUNNING;
            audit.record("system", "plugin.enable", state().name().toLowerCase(Locale.ROOT));
            logger.info("Black Door " + platform.pluginVersion() + " enabled on " + platform.info().type()
                    + " (state " + state() + ", mode " + settings.mode() + ").");
            for (String reason : degradedReasons()) {
                logger.warn("Degraded: " + reason);
            }
            if (!settings.setupCompleted()) {
                logger.info("First run: execute '/" + commands.label() + " setup' for the initial security check, then '/"
                        + commands.label() + " gui' in game to configure everything.");
            }
        } catch (RuntimeException e) {
            logger.error("Black Door failed to start; shutting down its components.", e);
            shutdownComponents();
            lifecycle = EngineState.STOPPED;
            throw new IllegalStateException("Black Door failed to start", e);
        }
    }

    /** Stops everything Black Door started. Safe to call more than once. */
    public synchronized void disable() {
        if (lifecycle == EngineState.STOPPED) {
            return;
        }
        lifecycle = EngineState.STOPPING;
        AuditLog currentAudit = audit;
        if (currentAudit != null) {
            currentAudit.record("system", "plugin.disable", "ok");
        }
        shutdownComponents();
        lifecycle = EngineState.STOPPED;
        logger.info("Black Door disabled.");
    }

    /**
     * Reloads configuration from disk. Blocking: call from a worker thread. All-or-nothing; on
     * failure the previous configuration stays active.
     */
    public ReloadResult reloadConfiguration(String actor) {
        GeneralSettings before = settings();
        ReloadResult result = config.load();
        lastReload = result;
        report(result, "Reload");
        if (result.success() && requiresRestart(before, settings())) {
            logger.warn("Worker or audit settings changed; they take effect after a restart.");
        }
        AuditLog currentAudit = audit;
        if (currentAudit != null) {
            currentAudit.record(actor, "config.reload",
                    result.success() ? "success" : "failed: " + result.errors().size() + " error(s)");
        }
        return result;
    }

    /** Backend only: wires sessions, ledger and the packet engine to the packet library. */
    private void startPacketLayer() {
        ledger = new ViolationLedger(new RateLimitedLogger(logger, time, 3, 1, 256), 10_000, 200);
        if (platform.info().type().isProxy() || !platform.protocol().isAvailable()) {
            return;
        }
        sessions = new SessionRegistry(config.current().get(CoreConfig.CHECKS).maxSessions(), time);
        packetEngine = new PacketSecurityEngine(sessions, ledger, time,
                () -> config.current().get(CoreConfig.CHECKS),
                () -> settings().mode() == GeneralSettings.Mode.ENFORCE,
                platform.info().minecraft().orElse(null),
                new RateLimitedLogger(logger, time, 3, 0.1, 16),
                platform.protocol().usesLegacyTransactions());
        io.github.intruder_tech.blackdoor.protocol.world.ShapeRegistry shapes = platform.protocol().shapeRegistry();
        if (shapes != null) {
            packetEngine.enableWorldTracking(new io.github.intruder_tech.blackdoor.world.WorldCache(
                    settings().worldMemoryMb() * 1024L * 1024L), shapes);
        }
        packetEngine.traceListener(traceRecorder);
        packetLayerAttached = platform.protocol().attach(packetEngine);
        if (!packetLayerAttached) {
            logger.error("Could not attach to " + platform.protocol().name() + "; packet checks are inactive.");
            return;
        }
        PacketSecurityEngine engineRef = packetEngine;
        io.github.intruder_tech.blackdoor.protocol.ProtocolBridge protocol = platform.protocol();
        heartbeat = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, BoundedExecutor.THREAD_PREFIX + "transactions");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                engineRef.heartbeat(protocol::sendTransaction);
            } catch (RuntimeException e) {
                logger.error("Transaction heartbeat failed", e);
            }
        }, TRANSACTION_INTERVAL_MS, TRANSACTION_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Changes one configuration value (comment-preserving), validates, writes and reloads.
     * Blocking: call from a worker thread. Audited with actor, key and outcome.
     */
    public ReloadResult applyChange(io.github.intruder_tech.blackdoor.config.ConfigFileSpec<?> spec, String path,
                                    String value, String actor) {
        ReloadResult result = config.applyChange(spec, path, value);
        lastReload = result;
        report(result, "Setting change");
        AuditLog currentAudit = audit;
        if (currentAudit != null) {
            currentAudit.record(actor, "setting.change " + spec.fileName() + ":" + path + "=" + value,
                    result.success() ? "success" : "rejected: " + result.errors().size() + " error(s)");
        }
        return result;
    }

    private void checkEnvironment(GeneralSettings settings) {
        if (settings.mode() == GeneralSettings.Mode.ENFORCE) {
            logger.warn("mode is 'enforce': checks set to 'mitigate' will block offending packets.");
        }
        if (!"en_US".equals(settings.language())) {
            logger.warn("Language '" + settings.language() + "' is not available yet; using en_US.");
        }
        Optional<MinecraftVersion> mc = platform.info().minecraft();
        if (mc.isEmpty()) {
            return;
        }
        VersionSupport.Status status = VersionSupport.classify(mc.get());
        if (status == VersionSupport.Status.NEWER_THAN_KNOWN) {
            logger.warn("Minecraft " + mc.get() + " is newer than this build knows (" + VersionSupport.LATEST_KNOWN
                    + "). Version-specific protections will stay disabled for it until Black Door is updated.");
        } else if (status == VersionSupport.Status.TOO_OLD) {
            logger.warn("Minecraft " + mc.get() + " is older than the supported minimum " + VersionSupport.MINIMUM + ".");
        }
        Optional<MinecraftVersion> reported = platform.protocol().serverVersion();
        if (reported.isPresent() && !reported.get().equals(mc.get())) {
            logger.warn("Server reports Minecraft " + mc.get() + " but " + platform.protocol().name()
                    + " reports " + reported.get() + ".");
        }
    }

    private static boolean requiresRestart(GeneralSettings before, GeneralSettings after) {
        return before.workerThreads() != after.workerThreads()
                || before.workerQueueCapacity() != after.workerQueueCapacity()
                || before.auditEnabled() != after.auditEnabled();
    }

    private void report(ReloadResult result, String what) {
        for (String note : result.notes()) {
            logger.info(what + ": " + note);
        }
        for (ConfigError warning : result.warnings()) {
            logger.warn(what + " warning: " + warning);
        }
        for (ConfigError error : result.errors()) {
            logger.error(what + " error: " + error);
        }
        if (!result.success()) {
            logger.error(what + " failed; " + (result.usingDefaults()
                    ? "bundled secure defaults are active." : "the previous configuration is still active."));
        }
    }

    private void shutdownComponents() {
        BlackDoorProvider.unregister(api);
        try {
            platform.menus().ifPresent(io.github.intruder_tech.blackdoor.core.menu.MenuPresenter::closeAll);
        } catch (RuntimeException e) {
            logger.error("Failed to close open menus", e);
        }
        java.util.concurrent.ScheduledExecutorService beat = heartbeat;
        if (beat != null) {
            beat.shutdownNow();
            try {
                if (!beat.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    logger.warn("Transaction heartbeat did not stop in time.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            heartbeat = null;
        }
        try {
            platform.protocol().detach();
        } catch (RuntimeException e) {
            logger.error("Failed to detach from the packet library", e);
        }
        packetLayerAttached = false;
        try {
            platform.scheduler().cancelAll();
        } catch (RuntimeException e) {
            logger.error("Failed to cancel scheduled tasks", e);
        }
        BoundedExecutor current = workers;
        if (current != null && !current.shutdown(SHUTDOWN_TIMEOUT)) {
            logger.warn("Worker threads did not stop within " + SHUTDOWN_TIMEOUT.toSeconds() + "s.");
        }
    }

    /** Lifecycle state, reported as DEGRADED while running with any degradation reason. */
    public EngineState state() {
        EngineState current = lifecycle;
        if (current == EngineState.RUNNING && !degradedReasons().isEmpty()) {
            return EngineState.DEGRADED;
        }
        return current;
    }

    /** Human-readable reasons why Black Door is not fully operational. */
    public List<String> degradedReasons() {
        List<String> reasons = new ArrayList<>();
        ReloadResult reload = lastReload;
        if (reload != null && reload.usingDefaults()) {
            reasons.add("configuration invalid; bundled defaults active (fix the files and run reload)");
        }
        if (!platform.info().type().isProxy() && !platform.protocol().isAvailable()) {
            reasons.add("packet library unavailable (" + platform.protocol().name() + "); packet-level protections inactive");
        } else if (!platform.info().type().isProxy() && lifecycle == EngineState.RUNNING && !packetLayerAttached) {
            reasons.add("could not attach to the packet library; packet checks inactive");
        }
        return reasons;
    }

    public Optional<PacketSecurityEngine> packetEngine() {
        return Optional.ofNullable(packetEngine);
    }

    public Optional<ViolationLedger> ledger() {
        return Optional.ofNullable(ledger);
    }

    public Optional<SessionRegistry> sessions() {
        return Optional.ofNullable(sessions);
    }

    public io.github.intruder_tech.blackdoor.core.settings.SettingsService settingsService() {
        return settingsService;
    }

    public io.github.intruder_tech.blackdoor.core.menu.BlackDoorMenus menus() {
        return menus;
    }

    public PacketSettings packetSettings() {
        return config.current().get(CoreConfig.CHECKS);
    }

    /** Queues blocking work on the bounded worker pool. Returns false if saturated or stopped. */
    public boolean submitWork(Runnable task) {
        BoundedExecutor current = workers;
        return current != null && current.trySubmit(task);
    }

    public Optional<BoundedExecutor.Stats> workerStats() {
        BoundedExecutor current = workers;
        return current == null ? Optional.empty() : Optional.of(current.stats());
    }

    public Optional<AuditLog> audit() {
        return Optional.ofNullable(audit);
    }

    public GeneralSettings settings() {
        return config.current().get(CoreConfig.GENERAL);
    }

    public ConfigSnapshot configSnapshot() {
        return config.current();
    }

    public Optional<ReloadResult> lastReload() {
        return Optional.ofNullable(lastReload);
    }

    public Platform platform() {
        return platform;
    }

    public TimeSource time() {
        return time;
    }

    public CommandRouter commands() {
        return commands;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }
}
