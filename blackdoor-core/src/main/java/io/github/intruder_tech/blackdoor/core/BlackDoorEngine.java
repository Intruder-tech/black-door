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
    private volatile io.github.intruder_tech.blackdoor.violations.FindingSink findings;
    private static final String KICK_MESSAGE = "Kicked by Black Door: suspicious game client.";
    private volatile io.github.intruder_tech.blackdoor.enforcement.BanBook bans;
    private volatile io.github.intruder_tech.blackdoor.enforcement.CaseBook cases;
    private volatile io.github.intruder_tech.blackdoor.enforcement.QuarantineBook quarantine;
    private volatile SessionRegistry sessions;
    private volatile PacketSecurityEngine packetEngine;
    private static final int SHAPES_PER_TICK = 500;
    private volatile io.github.intruder_tech.blackdoor.protocol.world.BlockShapes blockShapes;
    private volatile boolean blockShapesReady;
    private volatile long longestShapeBatchNanos;
    private volatile boolean packetLayerAttached;
    private volatile io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler packetHandler;
    private volatile io.github.intruder_tech.blackdoor.network.ConnectionGuard connectionGuard;
    private volatile java.util.concurrent.ScheduledExecutorService heartbeat;
    private volatile ProxyGate proxyGate;
    private volatile io.github.intruder_tech.blackdoor.auth.AuthService auth;
    private volatile java.util.Map<String, io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStrength>
            antiXrayAtStart = java.util.Map.of();

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
                new io.github.intruder_tech.blackdoor.core.command.sub.ShapesCommand(this),
                new VersionCommand(this),
                new ReloadCommand(this),
                new io.github.intruder_tech.blackdoor.core.command.sub.AuthCommand(this),
                new io.github.intruder_tech.blackdoor.core.command.sub.BansCommand(this),
                new io.github.intruder_tech.blackdoor.core.command.sub.CasesCommand(this),
                new io.github.intruder_tech.blackdoor.core.command.sub.QuarantineCommand(this),
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
        drawBanner();
        try {
            lastReload = config.load();
            report(lastReload, "Configuration");
            GeneralSettings settings = settings();
            workers = new BoundedExecutor("worker", settings.workerThreads(), settings.workerQueueCapacity(), logger);
            audit = new AuditLog(platform.dataDirectory().resolve("audit"), workers, time, logger, settings.auditEnabled());
            checkEnvironment(settings);
            // Paper read its anti-xray setting at startup: remember it, to tell "running" from "configured".
            antiXrayAtStart = antiXrayFiles().map(files -> io.github.intruder_tech.blackdoor.core.antixray
                    .StartupSnapshot.remember(platform.dataDirectory().resolve("antixray-startup.properties"),
                            java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),
                            files.worldStates(), logger::warn)).orElse(java.util.Map.of());
            startPacketLayer();
            if (!platform.info().type().isProxy()) {
                auth = new io.github.intruder_tech.blackdoor.auth.AuthService(platform.dataDirectory().resolve("auth"),
                        time, this::authSettings, new io.github.intruder_tech.blackdoor.auth.PasswordHasher(
                                io.github.intruder_tech.blackdoor.auth.PasswordHasher.DEFAULT_ITERATIONS), logger::error);
            }
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
                logger.info("First run: execute '/" + commands.label() + " setup' for the initial security check, then "
                        + (platform.info().type().isProxy()
                        ? "edit config.yml and network.yml and run '/" + commands.label() + " reload'."
                        : "'/" + commands.label() + " gui' in game to configure everything."));
            }
            announceGuard(settings);
        } catch (RuntimeException e) {
            logger.error("Black Door failed to start; shutting down its components.", e);
            shutdownComponents();
            lifecycle = EngineState.STOPPED;
            throw new IllegalStateException("Black Door failed to start", e);
        }
    }

    /** The start-up picture: a closed door. Plain ASCII, so every console shows it correctly. */
    private void drawBanner() {
        logger.info("  .----------.");
        logger.info("  |  .----.  |    B L A C K   D O O R");
        logger.info("  |  |    |  |    ---------------------------------");
        logger.info("  |  '----'  |    Made in India. Guard your world.");
        logger.info("  |        @ |");
        logger.info("  |  .----.  |    " + platform.pluginVersion() + " on " + platform.info().type());
        logger.info("  |__'----'__|    Closing the door...");
    }

    /** The last start-up line: how well the door is shut. */
    private void announceGuard(GeneralSettings settings) {
        if (!degradedReasons().isEmpty()) {
            logger.warn("[-o] The door is only partly guarded: see the warnings above.");
            return;
        }
        String guards = platform.info().type().isProxy() ? "join and ping guard"
                : io.github.intruder_tech.blackdoor.packets.PacketCheck.values().length + " packet checks";
        String mode = settings.mode() == GeneralSettings.Mode.ENFORCE ? "protection ON" : "watch only";
        logger.info("[#@] The door is guarded: " + guards + ", " + mode + ", ready in "
                + (time.currentTimeMillis() - startedAtMillis) + " ms.");
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
        findings = enforcingSink(ledger);
        if (!platform.info().type().isProxy()) {
            bans = new io.github.intruder_tech.blackdoor.enforcement.BanBook(
                    platform.dataDirectory().resolve("enforcement").resolve("bans.tsv"), time);
            if (bans.unreadableLines() > 0) {
                logger.warn("enforcement/bans.tsv: " + bans.unreadableLines() + " unreadable line(s) ignored.");
            }
            cases = new io.github.intruder_tech.blackdoor.enforcement.CaseBook(
                    platform.dataDirectory().resolve("enforcement").resolve("cases.tsv"), time);
            if (cases.unreadableLines() > 0) {
                logger.warn("enforcement/cases.tsv: " + cases.unreadableLines() + " unreadable line(s) ignored.");
            }
            quarantine = new io.github.intruder_tech.blackdoor.enforcement.QuarantineBook(
                    platform.dataDirectory().resolve("enforcement").resolve("quarantine.tsv"), time);
            if (quarantine.unreadableLines() > 0) {
                logger.warn("enforcement/quarantine.tsv: " + quarantine.unreadableLines()
                        + " unreadable line(s) ignored.");
            }
        }
        if (platform.info().type().isProxy()) {
            // Players connect to a proxy directly, so their addresses are real here.
            connectionGuard = newConnectionGuard(false);
            proxyGate = new ProxyGate(connectionGuard, new RateLimitedLogger(logger, time, 3, 0.1, 16));
            return;
        }
        if (!platform.protocol().isAvailable()) {
            return;
        }
        sessions = new SessionRegistry(config.current().get(CoreConfig.CHECKS).maxSessions(), time);
        packetEngine = new PacketSecurityEngine(sessions, findings, time,
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
        boolean forwarded = platform.securityFacts().behindProxy();
        io.github.intruder_tech.blackdoor.network.ConnectionGuard guard = newConnectionGuard(forwarded);
        connectionGuard = guard;
        packetHandler = new GuardedPacketHandler(packetEngine, guard,
                new RateLimitedLogger(logger, time, 3, 0.1, 16));
        packetLayerAttached = platform.protocol().attach(packetHandler);
        if (!packetLayerAttached) {
            logger.error("Could not attach to " + platform.protocol().name() + "; packet checks are inactive.");
            return;
        }
        startBlockShapes();
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

    /** Records every finding, then acts on the player if the enforcement policy says so. */
    private io.github.intruder_tech.blackdoor.violations.FindingSink enforcingSink(ViolationLedger records) {
        var policy = new io.github.intruder_tech.blackdoor.enforcement.EnforcementPolicy(
                this::enforcementSettings, 10_000);
        return finding -> {
            records.accept(finding);
            try {
                policy.accept(finding).ifPresent(this::act);
            } catch (RuntimeException e) {
                // Runs on a network thread: a fault here must never disturb the connection.
                logger.error("Enforcement failed", e);
            }
        };
    }

    private void act(io.github.intruder_tech.blackdoor.enforcement.EnforcementPolicy.Decision decision) {
        String name = io.github.intruder_tech.blackdoor.common.text.LogSanitizer
                .sanitize(decision.subject().displayName(), 32);
        String why = " (" + decision.findings() + " findings, last " + decision.checkId() + ")";
        if (!decision.kick()) {
            logger.info("Would kick " + name + why + "; enforcement mode is watch.");
            openCase(decision, name, io.github.intruder_tech.blackdoor.enforcement.CaseBook.Action.WOULD_KICK);
            return;
        }
        java.util.UUID id = decision.subject().uniqueId();
        platform.scheduler().runForPlayer(id, () -> {
            var rules = enforcementSettings();
            if (rules.mode() == io.github.intruder_tech.blackdoor.enforcement.EnforcementSettings.Mode.QUARANTINE) {
                if (!platform.exempt(id) && hold(id, name, "system", why)) {
                    openCase(decision, name,
                            io.github.intruder_tech.blackdoor.enforcement.CaseBook.Action.QUARANTINE);
                }
                return;
            }
            var book = bans;
            boolean ban = book != null && book.nextKickBans(id, rules);
            if (!platform.kick(id, ban ? banText(java.time.Duration.ofMinutes(rules.banMinutes())) : KICK_MESSAGE)) {
                return;
            }
            logger.warn("Kicked " + name + why + ".");
            record("system", "enforcement.kick " + name, decision.checkId());
            var banned = book == null ? Optional.empty() : book.recordKick(id, name, rules);
            if (banned.isPresent()) {
                logger.warn("Banned " + name + " for " + rules.banMinutes() + " minutes.");
                record("system", "enforcement.ban " + name, rules.banMinutes() + " minutes");
            }
            saveBans();
            openCase(decision, name, banned.isPresent()
                    ? io.github.intruder_tech.blackdoor.enforcement.CaseBook.Action.BAN
                    : io.github.intruder_tech.blackdoor.enforcement.CaseBook.Action.KICK);
        });
    }

    private void openCase(io.github.intruder_tech.blackdoor.enforcement.EnforcementPolicy.Decision decision,
                          String name, io.github.intruder_tech.blackdoor.enforcement.CaseBook.Action action) {
        var book = cases;
        if (book != null) {
            book.open(decision.subject().uniqueId(), name, action, decision.evidence());
            saveCases();
        }
    }

    private void saveCases() {
        var book = cases;
        if (book == null) {
            return;
        }
        boolean queued = submitWork(() -> {
            try {
                book.save();
            } catch (java.io.IOException e) {
                logger.error("Could not save enforcement cases; the latest will be lost on restart.", e);
            }
        });
        if (!queued) {
            logger.warn("Too busy to save enforcement cases now.");
        }
    }

    /** Puts a player in quarantine. Call on the player's thread. False if already held. */
    private boolean hold(java.util.UUID id, String name, String actor, String why) {
        var book = quarantine;
        if (book == null || !book.add(id, name, enforcementSettings().quarantineMinutes())) {
            return false;
        }
        logger.warn("Quarantined " + name + why + ".");
        record(actor, "enforcement.quarantine " + name, "held");
        platform.message(id, "Black Door put you in quarantine: you can move and chat, nothing else. "
                + "Staff can release you.");
        saveQuarantine();
        return true;
    }

    /** Staff quarantine of an online player. False if already held, exempt or not possible here. */
    public boolean quarantinePlayer(java.util.UUID id, String name, String actor) {
        if (quarantine == null || quarantine.holds(id)) {
            return false;
        }
        String clean = io.github.intruder_tech.blackdoor.common.text.LogSanitizer.sanitize(name, 32);
        platform.scheduler().runForPlayer(id, () -> {
            if (!platform.exempt(id)) {
                hold(id, clean, actor, " (by " + actor + ")");
            }
        });
        return true;
    }

    /** Frees a quarantined player by name. False if nobody of that name is held. */
    public boolean release(String name, String actor) {
        var book = quarantine;
        if (book == null) {
            return false;
        }
        var held = book.active().stream().filter(h -> h.name().equalsIgnoreCase(name)).findFirst();
        if (held.isEmpty()) {
            return false;
        }
        free(held.get().id(), held.get().name(), actor);
        return true;
    }

    private void free(java.util.UUID id, String name, String actor) {
        if (quarantine != null && quarantine.release(id)) {
            record(actor, "enforcement.release " + name, "released");
            platform.scheduler().runForPlayer(id, () -> platform.message(id,
                    "Black Door released you from quarantine."));
            saveQuarantine();
        }
    }

    /** Asked on every game event of a player, from any thread. */
    public boolean isQuarantined(java.util.UUID id) {
        var book = quarantine;
        return book != null && book.holds(id);
    }

    /** Players in quarantine; empty on proxies. */
    public Optional<io.github.intruder_tech.blackdoor.enforcement.QuarantineBook> quarantine() {
        return Optional.ofNullable(quarantine);
    }

    private void saveQuarantine() {
        var book = quarantine;
        if (book == null) {
            return;
        }
        boolean queued = submitWork(() -> {
            try {
                book.save();
            } catch (java.io.IOException e) {
                logger.error("Could not save quarantine; it will be lost on restart.", e);
            }
        });
        if (!queued) {
            logger.warn("Too busy to save quarantine now.");
        }
    }

    /** Saved cases for staff review; empty on proxies. */
    public Optional<io.github.intruder_tech.blackdoor.enforcement.CaseBook> cases() {
        return Optional.ofNullable(cases);
    }

    /**
     * Records the staff verdict on a case. Calling a kick or ban a false alarm also drops that
     * player's ban and kick count. Returns false if there is no such case.
     */
    public boolean judgeCase(int caseId, boolean falseAlarm, String actor) {
        var judged = cases().flatMap(book -> book.markFalseAlarm(caseId, falseAlarm));
        if (judged.isEmpty()) {
            return false;
        }
        var found = judged.get();
        if (falseAlarm && found.action() != io.github.intruder_tech.blackdoor.enforcement.CaseBook.Action.WOULD_KICK
                && bans != null) {
            bans.forget(found.player());
            saveBans();
        }
        if (falseAlarm
                && found.action() != io.github.intruder_tech.blackdoor.enforcement.CaseBook.Action.WOULD_KICK) {
            free(found.player(), found.name(), actor);
        }
        record(actor, "enforcement.case " + caseId + " " + found.name(), falseAlarm ? "false alarm" : "confirmed");
        saveCases();
        return true;
    }

    private static String banText(java.time.Duration left) {
        return "Banned by Black Door for " + io.github.intruder_tech.blackdoor.enforcement.BanBook
                .words(left.toMillis()) + ": suspicious game client.";
    }

    private void record(String actor, String action, String outcome) {
        AuditLog currentAudit = audit;
        if (currentAudit != null) {
            currentAudit.record(actor, action, outcome);
        }
    }

    private void saveBans() {
        var book = bans;
        if (book == null) {
            return;
        }
        boolean queued = submitWork(() -> {
            try {
                book.save();
            } catch (java.io.IOException e) {
                logger.error("Could not save temporary bans; they will be lost on restart.", e);
            }
        });
        if (!queued) {
            logger.warn("Too busy to save temporary bans now.");
        }
    }

    /** Temporary bans and recent kicks; empty on proxies. */
    public Optional<io.github.intruder_tech.blackdoor.enforcement.BanBook> bans() {
        return Optional.ofNullable(bans);
    }

    /** What to tell a banned player who tries to join; empty if they may join. Thread-safe. */
    public Optional<String> banMessage(java.util.UUID playerId) {
        return bans().flatMap(book -> book.active(playerId))
                .map(b -> banText(java.time.Duration.ofMillis(b.untilMillis() - time.currentTimeMillis())));
    }

    /** Lifts a temporary ban. Returns false if no running ban has that name. */
    public boolean unban(String name, String actor) {
        boolean lifted = bans().map(book -> book.lift(name)).orElse(false);
        if (lifted) {
            record(actor, "enforcement.unban " + io.github.intruder_tech.blackdoor.common.text.LogSanitizer
                    .sanitize(name, 32), "success");
            saveBans();
        }
        return lifted;
    }

    /** Where checks report. Null only before {@link #enable()}. */
    public io.github.intruder_tech.blackdoor.violations.FindingSink findings() {
        return findings;
    }

    public io.github.intruder_tech.blackdoor.enforcement.EnforcementSettings enforcementSettings() {
        return config.current().get(CoreConfig.ENFORCEMENT);
    }

    /** Builds the block shape table on the server thread, a batch per tick. */
    private void startBlockShapes() {
        var source = platform.blockShapeSource();
        if (source == null) {
            logger.info("Block shapes are not available on this server; movement prediction stays off.");
            return;
        }
        var table = new io.github.intruder_tech.blackdoor.protocol.world.BlockShapes();
        blockShapes = table;
        buildShapes(new io.github.intruder_tech.blackdoor.core.world.ShapeTableBuilder(
                platform.protocol()::blockState, source, table, SHAPES_PER_TICK));
    }

    private void buildShapes(io.github.intruder_tech.blackdoor.core.world.ShapeTableBuilder builder) {
        platform.scheduler().runGlobal(() -> {
            if (blockShapes == null) {
                return;
            }
            try {
                long start = System.nanoTime();
                boolean more = builder.step();
                longestShapeBatchNanos = Math.max(longestShapeBatchNanos, System.nanoTime() - start);
                if (more) {
                    buildShapes(builder);
                    return;
                }
                blockShapesReady = true;
                logger.info("Block shapes ready: " + blockShapes.known() + " states, longest batch "
                        + longestShapeBatchNanos / 1_000_000 + " ms.");
            } catch (RuntimeException e) {
                logger.error("Could not build block shapes; movement prediction stays off.", e);
                blockShapes = null;
            }
        });
    }

    /** The block shape table, if this server can provide one (it may still be building). */
    public Optional<io.github.intruder_tech.blackdoor.protocol.world.BlockShapes> blockShapes() {
        return Optional.ofNullable(blockShapes);
    }

    public boolean blockShapesReady() {
        return blockShapesReady && blockShapes != null;
    }

    private io.github.intruder_tech.blackdoor.network.ConnectionGuard newConnectionGuard(boolean forwarded) {
        return new io.github.intruder_tech.blackdoor.network.ConnectionGuard(ledger, time, this::networkSettings,
                () -> settings().mode() == GeneralSettings.Mode.ENFORCE, () -> forwarded, this::attackChanged);
    }

    private void attackChanged(boolean started) {
        if (!started) {
            logger.info("Attack mode ended.");
        } else if (connectionGuard != null && connectionGuard.warmingUp()) {
            logger.warn("Attack mode started during the warm-up after start: nothing is refused yet.");
        } else if (settings().mode() == GeneralSettings.Mode.ENFORCE && networkSettings().adaptive().mode()
                == io.github.intruder_tech.blackdoor.network.NetworkSettings.Mode.MITIGATE) {
            logger.warn("Attack mode started: unknown addresses are limited.");
        } else {
            logger.warn("Attack mode started (watch only): nothing is refused.");
        }
    }

    /** Adapters report players in game, so their addresses become known to adaptive mode. */
    public void playerJoined(java.util.UUID id, java.net.InetAddress address) {
        io.github.intruder_tech.blackdoor.network.ConnectionGuard guard = connectionGuard;
        if (guard != null) {
            try {
                guard.joined(id, address);
            } catch (RuntimeException e) {
                logger.error("Could not record a joined player", e);
            }
        }
    }

    public void playerLeft(java.util.UUID id) {
        io.github.intruder_tech.blackdoor.network.ConnectionGuard guard = connectionGuard;
        if (guard != null) {
            try {
                guard.left(id);
            } catch (RuntimeException e) {
                logger.error("Could not record a player leaving", e);
            }
        }
    }

    /** The login system; empty on proxies. */
    public Optional<io.github.intruder_tech.blackdoor.auth.AuthService> auth() {
        return Optional.ofNullable(auth);
    }

    public io.github.intruder_tech.blackdoor.auth.AuthSettings authSettings() {
        return config.current().get(CoreConfig.AUTH);
    }

    /** Join and ping limits for the proxy adapters; empty on backends. */
    public Optional<ProxyGate> proxyGate() {
        return Optional.ofNullable(proxyGate);
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

    /** Paper's anti-xray files; empty on anything but Paper-family servers. Server thread only. */
    public Optional<io.github.intruder_tech.blackdoor.core.antixray.AntiXrayFiles> antiXrayFiles() {
        return platform.info().type().isPaperFamily() ? platform.antiXrayFiles() : Optional.empty();
    }

    /** Configured and running anti-xray; empty when the files cannot be read. */
    public Optional<io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStatus> antiXrayStatus(
            io.github.intruder_tech.blackdoor.core.antixray.AntiXrayFiles files) {
        return Optional.ofNullable(files.worldStates()).map(configured ->
                new io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStatus(configured, antiXrayAtStart));
    }

    /**
     * Sets the strength of Paper's anti-xray in Paper's files; the server applies it at its next restart.
     * Blocking: call from a worker thread. Audited with actor and outcome.
     *
     * @return the number of files changed
     */
    public int setAntiXray(io.github.intruder_tech.blackdoor.core.antixray.AntiXrayFiles files,
                           io.github.intruder_tech.blackdoor.core.antixray.AntiXrayStrength strength,
                           String actor) throws java.io.IOException {
        String action = "antixray.set strength=" + strength.name().toLowerCase(Locale.ROOT);
        AuditLog currentAudit = audit;
        try {
            int changed = files.set(strength).size();
            if (currentAudit != null) {
                currentAudit.record(actor, action, "success: " + changed + " file(s)");
            }
            return changed;
        } catch (java.io.IOException e) {
            logger.warn("Could not change Paper's anti-xray setting: " + e.getMessage());
            if (currentAudit != null) {
                currentAudit.record(actor, action, "failed: " + e.getMessage());
            }
            throw e;
        } catch (RuntimeException e) {
            logger.error("Could not change Paper's anti-xray setting", e);
            if (currentAudit != null) {
                currentAudit.record(actor, action, "failed: " + e.getClass().getSimpleName());
            }
            throw e;
        }
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
        blockShapes = null;
        blockShapesReady = false;
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

    /** The handler attached to the packet library, if the packet layer was started. */
    public Optional<io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler> packetHandler() {
        return Optional.ofNullable(packetHandler);
    }

    public Optional<io.github.intruder_tech.blackdoor.network.ConnectionGuard> connectionGuard() {
        return Optional.ofNullable(connectionGuard);
    }

    public io.github.intruder_tech.blackdoor.network.NetworkSettings networkSettings() {
        return config.current().get(CoreConfig.NETWORK);
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
