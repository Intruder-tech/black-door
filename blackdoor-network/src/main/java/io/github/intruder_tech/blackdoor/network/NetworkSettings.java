package io.github.intruder_tech.blackdoor.network;

import io.github.intruder_tech.blackdoor.config.ConfigReader;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Parsed {@code network.yml}. Immutable. */
public record NetworkSettings(Mode mode, Limit connections, Limit pings, Limit logins, int maxHostnameLength,
                              int maxTrackedAddresses, Set<String> exemptAddresses, boolean exemptPrivate,
                              Set<String> allowedProxies, Adaptive adaptive) {

    /** Same meaning as the packet check modes. */
    public enum Mode { DISABLED, MONITOR, MITIGATE }

    /**
     * Login-flood handling: above {@code triggerPerSecond} logins from unknown addresses, they
     * share {@code unknownPerSecond} until the rate has been low for {@code calmSeconds}. Nothing is
     * refused in the first {@code warmupSeconds} after start, while the known addresses fill up.
     */
    public record Adaptive(Mode mode, int triggerPerSecond, int unknownPerSecond, int calmSeconds,
                           int warmupSeconds) {
        /** Without a warm-up. */
        public Adaptive(Mode mode, int triggerPerSecond, int unknownPerSecond, int calmSeconds) {
            this(mode, triggerPerSecond, unknownPerSecond, calmSeconds, 0);
        }

        public static Adaptive defaults() {
            return new Adaptive(Mode.MONITOR, 10, 2, 60, 300);
        }
    }

    /** Per-address allowance: {@code burst} at once, refilled at {@code perSecond}. */
    public record Limit(int burst, int perSecond) {
    }

    private static final Pattern ADDRESS_LIST = Pattern.compile("[0-9a-fA-F:., ]*");

    public NetworkSettings {
        exemptAddresses = Set.copyOf(exemptAddresses);
        allowedProxies = Set.copyOf(allowedProxies);
    }

    /** With adaptive mode off. */
    public NetworkSettings(Mode mode, Limit connections, Limit pings, Limit logins, int maxHostnameLength,
                           int maxTrackedAddresses, Set<String> exemptAddresses, boolean exemptPrivate,
                           Set<String> allowedProxies) {
        this(mode, connections, pings, logins, maxHostnameLength, maxTrackedAddresses, exemptAddresses, exemptPrivate,
                allowedProxies, new Adaptive(Mode.DISABLED, 10, 2, 60));
    }

    /** Without a proxy allowlist. */
    public NetworkSettings(Mode mode, Limit connections, Limit pings, Limit logins, int maxHostnameLength,
                           int maxTrackedAddresses, Set<String> exemptAddresses, boolean exemptPrivate) {
        this(mode, connections, pings, logins, maxHostnameLength, maxTrackedAddresses, exemptAddresses, exemptPrivate,
                Set.of());
    }

    public static NetworkSettings defaults() {
        // Generous on purpose: many players can share one address (NAT, schools, VPNs).
        return new NetworkSettings(Mode.MONITOR, new Limit(30, 3), new Limit(30, 5), new Limit(10, 1), 255, 50_000,
                Set.of(), true, Set.of(), Adaptive.defaults());
    }

    public static NetworkSettings parse(ConfigReader r) {
        NetworkSettings d = defaults();
        String exempt = r.getString("exempt-addresses", "", 4096, ADDRESS_LIST);
        String proxies = r.getString("allowed-proxies", "", 4096, ADDRESS_LIST);
        return new NetworkSettings(
                r.getEnum("mode", Mode.class, d.mode()),
                limit(r, "connections", d.connections()),
                limit(r, "pings", d.pings()),
                limit(r, "logins", d.logins()),
                r.getInt("max-hostname-length", d.maxHostnameLength(), 64, 32767),
                r.getInt("max-tracked-addresses", d.maxTrackedAddresses(), 100, 1_000_000),
                addresses(exempt),
                r.getBoolean("exempt-private-addresses", d.exemptPrivate()),
                addresses(proxies),
                adaptive(r, d.adaptive()));
    }

    private static Adaptive adaptive(ConfigReader r, Adaptive d) {
        return new Adaptive(
                r.getEnum("adaptive.mode", Mode.class, d.mode()),
                r.getInt("adaptive.trigger-per-second", d.triggerPerSecond(), 1, 10_000),
                r.getInt("adaptive.unknown-per-second", d.unknownPerSecond(), 1, 10_000),
                r.getInt("adaptive.calm-seconds", d.calmSeconds(), 10, 3600),
                r.getInt("adaptive.warmup-seconds", d.warmupSeconds(), 0, 3600));
    }

    private static Set<String> addresses(String list) {
        return Arrays.stream(list.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private static Limit limit(ConfigReader r, String path, Limit d) {
        return new Limit(r.getInt(path + ".burst", d.burst(), 1, 100_000),
                r.getInt(path + ".per-second", d.perSecond(), 1, 100_000));
    }
}
