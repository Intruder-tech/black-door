package io.github.intruder_tech.blackdoor.network;

import io.github.intruder_tech.blackdoor.common.ratelimit.TokenBucket;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.violations.Confidence;
import io.github.intruder_tech.blackdoor.violations.Finding;
import io.github.intruder_tech.blackdoor.violations.FindingSink;
import io.github.intruder_tech.blackdoor.violations.Subject;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pre-play connection protection (docs/NETWORK_SECURITY.md section 2): per-address rate limits for
 * handshakes, status pings and logins, plus basic handshake and name validation. Methods return
 * true when the connection should be refused, which happens only when the mode is MITIGATE and the
 * global mode is "enforce". Thread-safe. Memory is bounded: the least recently seen address is
 * forgotten first.
 *
 * <p>This does not and cannot absorb bandwidth floods (docs/DDOS_LIMITATIONS.md).
 */
public final class ConnectionGuard {

    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;
    private static final int MAX_NAME_LENGTH = 16;

    private enum Kind {
        CONNECTION("network.connection-rate"),
        PING("network.ping-rate"),
        LOGIN("network.login-rate");

        final String checkId;

        Kind(String checkId) {
            this.checkId = checkId;
        }
    }

    private static final class Tracked {
        final TokenBucket[] buckets = new TokenBucket[Kind.values().length];
        final long[] reportedAt = new long[Kind.values().length];
        final boolean[] reported = new boolean[Kind.values().length];
    }

    private final FindingSink sink;
    private final TimeSource time;
    private final Supplier<NetworkSettings> settings;
    private final BooleanSupplier enforcementAllowed;
    private final BooleanSupplier forwarded;
    private final Map<InetAddress, Tracked> addresses = new LinkedHashMap<>(64, 0.75f, true);
    private final LongAdder refused = new LongAdder();
    private final KnownAddresses known;
    private final long startedAt;
    private final FloodDetector flood;
    private final Consumer<Boolean> attackChanged;
    private final LongAdder floodRefused = new LongAdder();
    private long floodReportedAt;
    private boolean floodReported;

    /** Adaptive mode as seen by staff. */
    public record AttackStatus(boolean active, int knownAddresses, long refused) {
    }

    public ConnectionGuard(FindingSink sink, TimeSource time, Supplier<NetworkSettings> settings,
                           BooleanSupplier enforcementAllowed, BooleanSupplier forwarded) {
        this(sink, time, settings, enforcementAllowed, forwarded, started -> {
        });
    }

    /**
     * @param forwarded     true when a proxy forwards players to this server, so every connection
     *                      shares the proxy's address and carries forwarding data in the hostname
     * @param attackChanged told true when attack mode starts and false when it ends
     */
    public ConnectionGuard(FindingSink sink, TimeSource time, Supplier<NetworkSettings> settings,
                           BooleanSupplier enforcementAllowed, BooleanSupplier forwarded,
                           Consumer<Boolean> attackChanged) {
        this.attackChanged = Objects.requireNonNull(attackChanged, "attackChanged");
        this.known = new KnownAddresses(time);
        this.startedAt = time.nanoTime();
        this.flood = new FloodDetector(time);
        this.sink = Objects.requireNonNull(sink, "sink");
        this.time = Objects.requireNonNull(time, "time");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.enforcementAllowed = Objects.requireNonNull(enforcementAllowed, "enforcementAllowed");
        this.forwarded = Objects.requireNonNull(forwarded, "forwarded");
    }

    public boolean handshake(InetAddress address, int hostnameLength) {
        NetworkSettings cfg = settings.get();
        if (cfg.mode() == NetworkSettings.Mode.DISABLED) {
            return false;
        }
        boolean refuse = rate(cfg, address, Kind.CONNECTION, cfg.connections()).refuse();
        // At handshake the address is still the real TCP peer, even behind a forwarding proxy.
        if (!cfg.allowedProxies().isEmpty() && !cfg.allowedProxies().contains(address.getHostAddress())) {
            refuse |= report(cfg, "network.direct-join", new Subject(null, address.getHostAddress()),
                    Confidence.DETERMINISTIC, "connected without going through an allowed proxy");
        }
        if (!forwarded.getAsBoolean() && hostnameLength > cfg.maxHostnameLength()) {
            refuse |= report(cfg, "network.hostname", new Subject(null, address.getHostAddress()),
                    Confidence.HEURISTIC, "hostname of " + hostnameLength + " characters");
        }
        return count(refuse);
    }

    public boolean statusPing(InetAddress address) {
        NetworkSettings cfg = settings.get();
        if (cfg.mode() == NetworkSettings.Mode.DISABLED) {
            return false;
        }
        return count(rate(cfg, address, Kind.PING, cfg.pings()).refuse());
    }

    public boolean loginStart(InetAddress address, String name) {
        NetworkSettings cfg = settings.get();
        if (cfg.mode() == NetworkSettings.Mode.DISABLED) {
            return false;
        }
        Rate login = rate(cfg, address, Kind.LOGIN, cfg.logins());
        boolean refuse = login.refuse();
        if (!validName(name)) {
            refuse |= report(cfg, "network.login-name", new Subject(null, address.getHostAddress()),
                    Confidence.HEURISTIC, "login name of " + (name == null ? 0 : name.length())
                            + " characters or with characters no account uses");
        }
        return count(flooding(cfg, address, refuse, login.over()));
    }

    /** A player is in game; their address counts as known once they have stayed a minute. */
    public void joined(UUID id, InetAddress address) {
        known.joined(id, address);
    }

    public void left(UUID id) {
        known.left(id, settings.get().maxTrackedAddresses());
    }

    public AttackStatus attackStatus() {
        NetworkSettings cfg = settings.get();
        if (flood.expire(cfg.adaptive())) {
            attackChanged.accept(false);
        }
        return new AttackStatus(adaptiveOn(cfg) && flood.active(), known.size(), floodRefused.sum());
    }

    /** True while adaptive mode still refuses nothing because Black Door has only just started. */
    public boolean warmingUp() {
        return time.nanoTime() - startedAt < settings.get().adaptive().warmupSeconds() * 1_000_000_000L;
    }

    private static boolean adaptiveOn(NetworkSettings cfg) {
        return cfg.mode() != NetworkSettings.Mode.DISABLED && cfg.adaptive().mode() != NetworkSettings.Mode.DISABLED;
    }

    /** Adaptive mode: during a flood, unknown addresses share a small login allowance. */
    private boolean flooding(NetworkSettings cfg, InetAddress address, boolean alreadyRefused, boolean overOwnLimit) {
        if (!adaptiveOn(cfg) || forwarded.getAsBoolean() || exempt(cfg, address) || known.isKnown(address)) {
            return alreadyRefused;
        }
        boolean block = cfg.adaptive().mode() == NetworkSettings.Mode.MITIGATE && enforcementAllowed.getAsBoolean()
                && !warmingUp();
        if (flood.expire(cfg.adaptive())) {
            attackChanged.accept(false);
        }
        if (flood.record(cfg.adaptive())) {
            attackChanged.accept(true);
            sink.accept(new Finding("network.attack-mode", new Subject(null, address.getHostAddress()),
                    Confidence.HEURISTIC, "more than " + cfg.adaptive().triggerPerSecond()
                    + " logins per second from unknown addresses", time.currentTimeMillis(), false));
        }
        if (alreadyRefused) {
            return true;
        }
        // An address over its own limit never draws on the shared allowance, even while the
        // per-address limits only watch: one address could otherwise lock out every new player.
        if (overOwnLimit && flood.active()) {
            if (block) {
                floodRefused.increment();
            }
            return block;
        }
        if (flood.tryAllow()) {
            return false;
        }
        if (reportFloodRefusal()) {
            sink.accept(new Finding("network.unknown-login", new Subject(null, address.getHostAddress()),
                    Confidence.HEURISTIC, "unknown address during a login flood", time.currentTimeMillis(), block));
        }
        if (block) {
            floodRefused.increment();
        }
        return block;
    }

    private synchronized boolean reportFloodRefusal() {
        long now = time.nanoTime();
        if (floodReported && now - floodReportedAt < REPORT_INTERVAL_NANOS) {
            return false;
        }
        floodReported = true;
        floodReportedAt = now;
        return true;
    }

    public long refused() {
        return refused.sum();
    }

    public int trackedAddresses() {
        synchronized (addresses) {
            return addresses.size();
        }
    }

    /** Printable ASCII without spaces, 1..16: covers Java accounts and Bedrock bridge prefixes. */
    private static boolean validName(String name) {
        return name != null && !name.isEmpty() && name.length() <= MAX_NAME_LENGTH
                && name.chars().allMatch(c -> c > 0x20 && c < 0x7f);
    }

    private boolean count(boolean refuse) {
        if (refuse) {
            refused.increment();
        }
        return refuse;
    }

    /** Whether the address is over its limit, and whether that limit itself refuses it. */
    private record Rate(boolean over, boolean refuse) {
    }

    private Rate rate(NetworkSettings cfg, InetAddress address, Kind kind, NetworkSettings.Limit limit) {
        if (forwarded.getAsBoolean() || exempt(cfg, address)) {
            return new Rate(false, false);
        }
        int k = kind.ordinal();
        boolean report = false;
        synchronized (addresses) {
            Tracked tracked = addresses.computeIfAbsent(address, a -> new Tracked());
            while (addresses.size() > cfg.maxTrackedAddresses()) {
                var eldest = addresses.keySet().iterator();
                eldest.next();
                eldest.remove();
            }
            if (tracked.buckets[k] == null) {
                tracked.buckets[k] = new TokenBucket(limit.burst(), limit.perSecond(), time);
            }
            if (tracked.buckets[k].tryAcquire()) {
                return new Rate(false, false);
            }
            long now = time.nanoTime();
            if (!tracked.reported[k] || now - tracked.reportedAt[k] >= REPORT_INTERVAL_NANOS) {
                tracked.reported[k] = true;
                tracked.reportedAt[k] = now;
                report = true;
            }
        }
        if (report) {
            return new Rate(true, report(cfg, kind.checkId, new Subject(null, address.getHostAddress()), Confidence.HEURISTIC,
                    "more than " + limit.burst() + " at once, " + limit.perSecond() + " per second sustained"));
        }
        return new Rate(true, mitigating(cfg));
    }

    private static boolean exempt(NetworkSettings cfg, InetAddress address) {
        if (cfg.exemptAddresses().contains(address.getHostAddress())) {
            return true;
        }
        if (!cfg.exemptPrivate()) {
            return false;
        }
        byte[] raw = address.getAddress();
        boolean uniqueLocal = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
        return uniqueLocal || address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isAnyLocalAddress();
    }

    private boolean mitigating(NetworkSettings cfg) {
        return cfg.mode() == NetworkSettings.Mode.MITIGATE && enforcementAllowed.getAsBoolean();
    }

    private boolean report(NetworkSettings cfg, String checkId, Subject subject, Confidence confidence, String detail) {
        boolean mitigate = mitigating(cfg);
        sink.accept(new Finding(checkId, subject, confidence, detail, time.currentTimeMillis(), mitigate));
        return mitigate;
    }
}
