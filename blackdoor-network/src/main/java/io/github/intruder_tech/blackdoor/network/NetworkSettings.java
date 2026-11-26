package io.github.intruder_tech.blackdoor.network;

import io.github.intruder_tech.blackdoor.config.ConfigReader;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Parsed {@code network.yml}. Immutable. */
public record NetworkSettings(Mode mode, Limit connections, Limit pings, Limit logins, int maxHostnameLength,
                              int maxTrackedAddresses, Set<String> exemptAddresses, boolean exemptPrivate) {

    /** Same meaning as the packet check modes. */
    public enum Mode { DISABLED, MONITOR, MITIGATE }

    /** Per-address allowance: {@code burst} at once, refilled at {@code perSecond}. */
    public record Limit(int burst, int perSecond) {
    }

    private static final Pattern ADDRESS_LIST = Pattern.compile("[0-9a-fA-F:., ]*");

    public NetworkSettings {
        exemptAddresses = Set.copyOf(exemptAddresses);
    }

    public static NetworkSettings defaults() {
        // Generous on purpose: many players can share one address (NAT, schools, VPNs).
        return new NetworkSettings(Mode.MONITOR, new Limit(30, 3), new Limit(30, 5), new Limit(10, 1), 255, 50_000,
                Set.of(), true);
    }

    public static NetworkSettings parse(ConfigReader r) {
        NetworkSettings d = defaults();
        String exempt = r.getString("exempt-addresses", "", 4096, ADDRESS_LIST);
        return new NetworkSettings(
                r.getEnum("mode", Mode.class, d.mode()),
                limit(r, "connections", d.connections()),
                limit(r, "pings", d.pings()),
                limit(r, "logins", d.logins()),
                r.getInt("max-hostname-length", d.maxHostnameLength(), 64, 32767),
                r.getInt("max-tracked-addresses", d.maxTrackedAddresses(), 100, 1_000_000),
                Arrays.stream(exempt.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet()),
                r.getBoolean("exempt-private-addresses", d.exemptPrivate()));
    }

    private static Limit limit(ConfigReader r, String path, Limit d) {
        return new Limit(r.getInt(path + ".burst", d.burst(), 1, 100_000),
                r.getInt(path + ".per-second", d.perSecond(), 1, 100_000));
    }
}
