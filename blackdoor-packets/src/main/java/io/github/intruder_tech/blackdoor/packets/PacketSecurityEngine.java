package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler;
import io.github.intruder_tech.blackdoor.protocol.packet.PacketVerdict;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.state.AttachmentKey;
import io.github.intruder_tech.blackdoor.state.PendingTeleports;
import io.github.intruder_tech.blackdoor.state.PlayerSession;
import io.github.intruder_tech.blackdoor.state.SessionRegistry;
import io.github.intruder_tech.blackdoor.state.TransactionTracker;
import io.github.intruder_tech.blackdoor.violations.Finding;
import io.github.intruder_tech.blackdoor.violations.FindingSink;
import io.github.intruder_tech.blackdoor.violations.Subject;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Deterministic packet validation (Phase 2, milestone 2.1). Runs on each connection's network
 * thread. Never throws: an internal error lets the packet pass and is counted (fail-open for our
 * own bugs, docs/ARCHITECTURE.md section 7). Packets are dropped only when the check is set to
 * MITIGATE <em>and</em> the global mode is "enforce".
 */
public final class PacketSecurityEngine implements PacketHandler {

    /** Vanilla clamps coordinates to these magnitudes; no legitimate client exceeds them. */
    static final double MAX_HORIZONTAL = 3.0E7;
    static final double MAX_VERTICAL = 2.0E7;
    static final float PITCH_LIMIT = 90.0f;
    static final float PITCH_EPSILON = 1.0E-4f;

    private static final AttachmentKey<FloodState> FLOOD = AttachmentKey.create("packet-flood");
    private static final AttachmentKey<GameModeState> GAME_MODE = AttachmentKey.create("game-mode");

    private final SessionRegistry sessions;
    private final FindingSink sink;
    private final TimeSource time;
    private final Supplier<PacketSettings> settings;
    private final BooleanSupplier enforcementAllowed;
    private final MinecraftVersion serverVersion;
    private final RateLimitedLogger errorLog;

    private final LongAdder processed = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LatencyHistogram latency = new LatencyHistogram();
    private final LongAdder transactionsSent = new LongAdder();
    private final LongAdder consumedTransactions = new LongAdder();
    private final boolean legacyTransactions;

    public PacketSecurityEngine(SessionRegistry sessions, FindingSink sink, TimeSource time,
                                Supplier<PacketSettings> settings, BooleanSupplier enforcementAllowed,
                                MinecraftVersion serverVersion, RateLimitedLogger errorLog) {
        this(sessions, sink, time, settings, enforcementAllowed, serverVersion, errorLog, false);
    }

    public PacketSecurityEngine(SessionRegistry sessions, FindingSink sink, TimeSource time,
                                Supplier<PacketSettings> settings, BooleanSupplier enforcementAllowed,
                                MinecraftVersion serverVersion, RateLimitedLogger errorLog,
                                boolean legacyTransactions) {
        this.legacyTransactions = legacyTransactions;
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.time = Objects.requireNonNull(time, "time");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.enforcementAllowed = Objects.requireNonNull(enforcementAllowed, "enforcementAllowed");
        this.serverVersion = serverVersion;
        this.errorLog = Objects.requireNonNull(errorLog, "errorLog");
    }

    @Override
    public PacketVerdict onInbound(ConnectionInfo connection, ConnectionState state, int wireSize, ServerboundPacket packet) {
        long start = time.nanoTime();
        try {
            if (state != ConnectionState.PLAY) {
                return PacketVerdict.PASS;
            }
            Optional<PlayerSession> found = sessions.session(connection);
            if (found.isEmpty()) {
                return PacketVerdict.PASS;
            }
            PlayerSession session = found.get();
            session.countInbound();
            TraceListener trace = traceListener;
            if (trace != null) {
                trace.inbound(session, packet, start);
            }
            PacketSettings cfg = settings.get();
            if (packet instanceof ServerboundPacket.TransactionResponse response) {
                return transactionResponse(session, cfg, response, start);
            }
            boolean drop = inspect(session, cfg, wireSize, packet, start);
            if (drop) {
                dropped.increment();
                return PacketVerdict.DROP;
            }
            return PacketVerdict.PASS;
        } catch (RuntimeException e) {
            errors.increment();
            errorLog.error("packet-engine", "Packet engine error (packet allowed)", e);
            return PacketVerdict.PASS;
        } finally {
            processed.increment();
            latency.record(time.nanoTime() - start);
        }
    }

    private boolean inspect(PlayerSession session, PacketSettings cfg, int wireSize, ServerboundPacket packet, long now) {
        FloodState flood = session.attachment(FLOOD, () -> new FloodState(cfg, now));
        boolean drop = rate(session, cfg, flood, FloodState.Category.TOTAL, now);
        // Replies the server waits for still count towards the rate, but dropping one disconnects the player.
        if (packet instanceof ServerboundPacket.Reply || packet instanceof ServerboundPacket.TeleportConfirm) {
            drop = false;
        }
        if (packet instanceof ServerboundPacket.Undecodable u) {
            drop |= undecodable(session, cfg, u.kind());
        } else if (packet instanceof ServerboundPacket.Movement m) {
            drop |= rate(session, cfg, flood, FloodState.Category.MOVEMENT, now);
            drop |= movement(session, cfg, wireSize, m);
        } else if (packet instanceof ServerboundPacket.TeleportConfirm t) {
            drop |= teleportConfirm(session, cfg, t);
        } else if (packet instanceof ServerboundPacket.HeldItemChange h) {
            if (h.slot() < 0 || h.slot() > 8) {
                drop |= report(session, cfg, PacketCheck.INVALID_SLOT, "slot=" + h.slot());
            }
        } else if (packet instanceof ServerboundPacket.InteractEntity i) {
            drop |= rate(session, cfg, flood, FloodState.Category.INTERACT, now);
            if (session.entityId() != -1 && i.entityId() == session.entityId()) {
                drop |= report(session, cfg, PacketCheck.SELF_INTERACT, "action=" + i.action());
            }
        } else if (packet instanceof ServerboundPacket.ArmSwing) {
            drop |= rate(session, cfg, flood, FloodState.Category.INTERACT, now);
        } else if (packet instanceof ServerboundPacket.Text t) {
            drop |= rate(session, cfg, flood, FloodState.Category.TEXT, now);
            int limit = switch (t.kind()) {
                case CHAT -> cfg.maxChatLength();
                case COMMAND -> cfg.maxCommandLength();
                case TAB_COMPLETE -> cfg.maxTabCompleteLength();
            };
            if (t.length() > limit) {
                drop |= report(session, cfg, PacketCheck.OVERSIZED, t.kind() + " length " + t.length() + " > " + limit);
            }
        } else {
            drop |= exploits(session, cfg, wireSize, packet, now);
        }
        return drop;
    }

    private boolean undecodable(PlayerSession session, PacketSettings cfg, ServerboundPacket.Undecodable.Kind kind) {
        PacketCheck check = switch (kind) {
            case SIGN -> PacketCheck.SIGN;
            case BOOK -> PacketCheck.BOOK;
            case PAYLOAD -> PacketCheck.PAYLOAD;
            case CHAT, COMMAND, TAB_COMPLETE -> PacketCheck.OVERSIZED;
            case CREATIVE_ITEM -> PacketCheck.CREATIVE_ITEM;
        };
        return report(session, cfg, check, kind.name().toLowerCase(java.util.Locale.ROOT)
                + " packet could not be decoded (over the protocol's limits)");
    }

    /** Exploit guards (docs/EXPLOIT_PREVENTION.md). Details carry numbers only, never client text. */
    private boolean exploits(PlayerSession session, PacketSettings cfg, int wireSize, ServerboundPacket packet,
                             long now) {
        PacketSettings.ExploitLimits max = cfg.exploits();
        if (packet instanceof ServerboundPacket.PluginMessage p) {
            boolean brand = p.channel().equals("minecraft:brand") || p.channel().equals("MC|Brand");
            int limit = brand ? max.brandBytes() : max.payloadBytes();
            if (p.channel().length() > max.channelLength() || p.length() > limit) {
                return report(session, cfg, PacketCheck.PAYLOAD, (brand ? "brand" : "plugin message")
                        + " channel length " + p.channel().length() + ", data " + p.length() + " bytes");
            }
        } else if (packet instanceof ServerboundPacket.BookEdit b) {
            if (b.pages() > max.bookPages() || b.longestPage() > max.pageLength() || b.titleLength() > max.titleLength()) {
                return report(session, cfg, PacketCheck.BOOK, "pages " + b.pages() + ", longest page "
                        + b.longestPage() + ", title " + b.titleLength());
            }
        } else if (packet instanceof ServerboundPacket.SignUpdate s) {
            if (s.lines() > 4 || s.longestLine() > max.signLineLength()) {
                return report(session, cfg, PacketCheck.SIGN, "lines " + s.lines() + ", longest " + s.longestLine());
            }
        } else if (packet instanceof ServerboundPacket.CreativeItem c) {
            return creative(session, cfg, wireSize, c, now);
        }
        return false;
    }

    private boolean creative(PlayerSession session, PacketSettings cfg, int wireSize,
                             ServerboundPacket.CreativeItem c, long now) {
        PacketSettings.ExploitLimits max = cfg.exploits();
        boolean drop = false;
        // Slot -1 drops the item; 45 is the off hand.
        if (c.slot() < -1 || c.slot() > 45 || c.amount() < 0 || c.amount() > max.creativeAmount()
                || wireSize > max.creativeBytes()) {
            drop |= report(session, cfg, PacketCheck.CREATIVE_ITEM,
                    "slot " + c.slot() + ", amount " + c.amount() + ", " + wireSize + " bytes");
        }
        if (c.depth() > max.itemDepth() || c.elements() > max.itemElements()) {
            drop |= report(session, cfg, PacketCheck.ITEM_DATA,
                    "item data depth " + c.depth() + ", elements " + c.elements());
        }
        // No game mode seen (plugin loaded mid-session) means no evidence.
        GameModeState mode = session.attachment(GAME_MODE, () -> null);
        if (mode != null && !mode.mayUseCreative(now)) {
            drop |= report(session, cfg, PacketCheck.GAME_MODE, "creative action in slot " + c.slot());
        }
        return drop;
    }

    private boolean movement(PlayerSession session, PacketSettings cfg, int wireSize, ServerboundPacket.Movement m) {
        boolean drop = false;
        boolean valid = true;
        if (wireSize > cfg.maxMovementBytes()) {
            drop |= report(session, cfg, PacketCheck.OVERSIZED, "movement packet " + wireSize + " bytes");
        }
        if (m.hasPosition()) {
            if (!Double.isFinite(m.x()) || !Double.isFinite(m.y()) || !Double.isFinite(m.z())) {
                drop |= report(session, cfg, PacketCheck.INVALID_POSITION, "non-finite position");
                valid = false;
            } else if (Math.abs(m.x()) > MAX_HORIZONTAL || Math.abs(m.z()) > MAX_HORIZONTAL || Math.abs(m.y()) > MAX_VERTICAL) {
                drop |= report(session, cfg, PacketCheck.INVALID_POSITION,
                        String.format(java.util.Locale.ROOT, "position %.1f %.1f %.1f out of world", m.x(), m.y(), m.z()));
                valid = false;
            }
        }
        if (m.hasRotation()) {
            if (!Float.isFinite(m.yaw()) || !Float.isFinite(m.pitch())) {
                drop |= report(session, cfg, PacketCheck.INVALID_ROTATION, "non-finite rotation");
                valid = false;
            } else if (Math.abs(m.pitch()) > PITCH_LIMIT + PITCH_EPSILON) {
                drop |= report(session, cfg, PacketCheck.INVALID_ROTATION, "pitch=" + m.pitch());
                valid = false;
            }
        }
        if (valid && (m.hasPosition() || m.hasRotation())) {
            var h = session.history();
            boolean known = h.size() > 0;
            if (m.hasPosition() || known) {
                h.record(m.hasPosition() ? m.x() : h.x(0), m.hasPosition() ? m.y() : h.y(0), m.hasPosition() ? m.z() : h.z(0),
                        m.hasRotation() ? m.yaw() : known ? h.yaw(0) : 0f,
                        m.hasRotation() ? m.pitch() : known ? h.pitch(0) : 0f, m.onGround());
            }
        }
        return drop;
    }

    private boolean teleportConfirm(PlayerSession session, PacketSettings cfg, ServerboundPacket.TeleportConfirm t) {
        PendingTeleports.Result result = session.teleports().confirm(t.teleportId());
        // No evidence, no verdict: if we did not see this session's login (e.g. plugin loaded mid-session)
        // or a translator sits in between, unknown ids are not proof of anything.
        boolean observedLogin = session.entityId() != -1;
        boolean translated = serverVersion != null && session.clientVersion() != null
                && !serverVersion.equals(session.clientVersion());
        if (result == PendingTeleports.Result.UNKNOWN && observedLogin && !translated) {
            return report(session, cfg, PacketCheck.TELEPORT_CONFIRM, "teleport id " + t.teleportId());
        }
        return false;
    }

    /**
     * Replies to Black Door's own transactions are always consumed (the server never sent them, so
     * it must not see the replies). Replies to other plugins' pings pass untouched.
     */
    private PacketVerdict transactionResponse(PlayerSession session, PacketSettings cfg,
                                              ServerboundPacket.TransactionResponse response, long now) {
        TransactionTracker tracker = session.transactions();
        if (tracker == null || !TransactionTracker.isOurs(response.id(), tracker.legacy())) {
            return PacketVerdict.PASS;
        }
        TransactionTracker.Result result = tracker.acknowledge(response.id(), now);
        session.velocities().acknowledgedUpTo(tracker.acknowledgedSequence());
        io.github.intruder_tech.blackdoor.world.PlayerWorldView worldView = session.attachment(WORLD, () -> null);
        if (worldView != null) {
            worldView.acknowledged(tracker.acknowledgedSequence());
        }
        if (result == TransactionTracker.Result.OUT_OF_ORDER) {
            report(session, cfg, PacketCheck.TRANSACTION, "reply skipped earlier connection checks");
        } else if (result == TransactionTracker.Result.UNKNOWN) {
            report(session, cfg, PacketCheck.TRANSACTION, "reply to a connection check that was never sent");
        }
        consumedTransactions.increment();
        return PacketVerdict.DROP;
    }

    /**
     * Sends one transaction to every session that has finished logging in. Called by the heartbeat
     * thread; {@code sender} performs the network write (thread-safe in Netty).
     */
    public void heartbeat(java.util.function.BiPredicate<Object, Integer> sender) {
        long now = time.nanoTime();
        sessions.forEach(session -> {
            if (session.entityId() == -1) {
                return;
            }
            try {
                TransactionTracker tracker = session.enableTransactions(legacyTransactions);
                if (sender.test(session.key(), tracker.next(now))) {
                    transactionsSent.increment();
                }
            } catch (RuntimeException e) {
                errors.increment();
                errorLog.error("packet-heartbeat", "Transaction heartbeat error", e);
            }
        });
    }

    private boolean rate(PlayerSession session, PacketSettings cfg, FloodState flood, FloodState.Category category, long now) {
        if (flood.tryAcquire(category, now)) {
            return false;
        }
        CheckMode mode = cfg.mode(PacketCheck.FLOOD);
        if (mode == CheckMode.DISABLED) {
            return false;
        }
        long excess = flood.takeReport(category, now);
        if (excess >= 0) {
            report(session, cfg, PacketCheck.FLOOD, category.name().toLowerCase(java.util.Locale.ROOT)
                    + " rate exceeded (" + excess + " excess packets in last report window)");
        }
        return mode == CheckMode.MITIGATE && enforcementAllowed.getAsBoolean();
    }

    private boolean report(PlayerSession session, PacketSettings cfg, PacketCheck check, String detail) {
        CheckMode mode = cfg.mode(check);
        if (mode == CheckMode.DISABLED) {
            return false;
        }
        boolean mitigate = mode == CheckMode.MITIGATE && enforcementAllowed.getAsBoolean();
        sink.accept(new Finding(check.id(), new Subject(session.uniqueId(), session.name()), check.confidence(),
                detail, time.currentTimeMillis(), mitigate));
        return mitigate;
    }

    @Override
    public void onOutbound(ConnectionInfo connection, ClientboundPacket packet) {
        try {
            Optional<PlayerSession> found = sessions.session(connection);
            if (found.isEmpty()) {
                return;
            }
            PlayerSession session = found.get();
            TraceListener trace = traceListener;
            if (trace != null) {
                trace.outbound(session, packet, time.nanoTime());
            }
            if (packet instanceof ClientboundPacket.JoinGame j) {
                session.entityId(j.entityId());
            } else if (packet instanceof ClientboundPacket.GameModeChange g) {
                session.attachment(GAME_MODE, GameModeState::new).update(g.creative(), time.nanoTime());
            } else if (packet instanceof ClientboundPacket.SyncPosition s) {
                session.teleports().sent(s.teleportId());
            } else if (packet instanceof ClientboundPacket.Velocity v) {
                if (v.entityId() == session.entityId()) {
                    TransactionTracker tracker = session.transactions();
                    session.velocities().add(v.x(), v.y(), v.z(), tracker == null ? 0 : tracker.nextSequence());
                }
            } else if (packet instanceof ClientboundPacket.EntitySpawn e) {
                session.entities().spawn(e.entityId(), e.x(), e.y(), e.z());
            } else if (packet instanceof ClientboundPacket.EntityMove e) {
                session.entities().move(e.entityId(), e.dx(), e.dy(), e.dz());
            } else if (packet instanceof ClientboundPacket.EntityTeleport e) {
                session.entities().teleport(e.entityId(), e.x(), e.y(), e.z());
            } else if (packet instanceof ClientboundPacket.EntityRemove e) {
                for (int id : e.entityIds()) {
                    session.entities().remove(id);
                }
            } else {
                worldEvent(session, packet);
            }
        } catch (RuntimeException e) {
            errors.increment();
            errorLog.error("packet-engine", "Packet engine error on outbound packet", e);
        }
    }

    @Override
    public void onDisconnect(Object connectionKey) {
        sessions.remove(connectionKey).ifPresent(session -> {
            TraceListener trace = traceListener;
            if (trace != null) {
                trace.disconnected(session);
            }
            io.github.intruder_tech.blackdoor.world.PlayerWorldView view = session.attachment(WORLD, () -> null);
            if (view != null) {
                view.releaseAll();
            }
        });
    }

    private static final AttachmentKey<io.github.intruder_tech.blackdoor.world.PlayerWorldView> WORLD =
            AttachmentKey.create("world-view");
    private static final java.util.concurrent.atomic.AtomicLong PRIVATE_WORLDS = new java.util.concurrent.atomic.AtomicLong();

    private volatile io.github.intruder_tech.blackdoor.world.WorldCache worldCache;
    private volatile io.github.intruder_tech.blackdoor.protocol.world.ShapeRegistry shapeRegistry;

    private volatile TraceListener traceListener;

    /** Installs (or with null removes) the golden-trace recorder hook. */
    public void traceListener(TraceListener listener) {
        this.traceListener = listener;
    }

    /** Enables world tracking (ADR-017). Call before attaching to the packet layer. */
    public void enableWorldTracking(io.github.intruder_tech.blackdoor.world.WorldCache cache,
                                    io.github.intruder_tech.blackdoor.protocol.world.ShapeRegistry registry) {
        this.worldCache = cache;
        this.shapeRegistry = registry;
    }

    public java.util.Optional<io.github.intruder_tech.blackdoor.world.WorldCache> worldCache() {
        return java.util.Optional.ofNullable(worldCache);
    }

    /** The player's world view, if world tracking is enabled and the player has one. */
    public java.util.Optional<io.github.intruder_tech.blackdoor.world.PlayerWorldView> worldView(PlayerSession session) {
        return java.util.Optional.ofNullable(session.attachment(WORLD, () -> null));
    }

    private io.github.intruder_tech.blackdoor.world.PlayerWorldView view(PlayerSession session) {
        return session.attachment(WORLD, () -> new io.github.intruder_tech.blackdoor.world.PlayerWorldView(
                worldCache, "p:" + PRIVATE_WORLDS.incrementAndGet()));
    }

    private void worldEvent(PlayerSession session, ClientboundPacket packet) {
        if (worldCache == null || shapeRegistry == null) {
            return;
        }
        if (packet instanceof ClientboundPacket.WorldChange w) {
            view(session).changeWorld(w.worldName());
        } else if (packet instanceof ClientboundPacket.ChunkData c) {
            view(session).chunk(c.chunkX(), c.chunkZ(), c.minSectionY(), c.blocks(), shapeRegistry);
        } else if (packet instanceof ClientboundPacket.ChunkUnload u) {
            view(session).unload(u.chunkX(), u.chunkZ());
        } else if (packet instanceof ClientboundPacket.BlockChange b) {
            TransactionTracker tracker = session.transactions();
            view(session).blockChange(b.x(), b.y(), b.z(), shapeRegistry.classify(b.blockStateId()),
                    tracker == null ? Long.MAX_VALUE : tracker.nextSequence());
        }
    }

    public Stats stats() {
        return new Stats(processed.sum(), dropped.sum(), errors.sum(), sessions.size(), sessions.rejected(),
                latency.percentileUpperBoundNanos(50), latency.percentileUpperBoundNanos(99),
                transactionsSent.sum(), consumedTransactions.sum());
    }

    /** Engine counters; latencies are log2 bucket upper bounds in nanoseconds. */
    public record Stats(long processed, long dropped, long errors, int sessions, long sessionsRejected,
                        long p50Nanos, long p99Nanos, long transactionsSent, long transactionsAcknowledged) {
    }
}
