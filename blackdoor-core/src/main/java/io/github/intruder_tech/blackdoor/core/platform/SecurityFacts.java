package io.github.intruder_tech.blackdoor.core.platform;

/**
 * Security-relevant facts about the server, read by the platform adapter for /blackdoor setup.
 * {@code null} means "unknown on this platform".
 *
 * @param onlineMode           server authenticates players with Mojang itself
 * @param bungeeCordForwarding BungeeCord legacy IP/UUID forwarding is enabled (spigot.yml)
 * @param boundToAllInterfaces server listens on every network interface (empty server-ip)
 * @param velocityForwarding   Velocity modern forwarding is enabled (Paper configuration)
 * @param weakVelocitySecret   that forwarding's secret is empty or too short; the secret itself is
 *                             never kept
 * @param worldsWithoutAntiXray worlds where the built-in anti-xray of Paper is off
 */
public record SecurityFacts(Boolean onlineMode, Boolean bungeeCordForwarding, Boolean boundToAllInterfaces,
                            Boolean velocityForwarding, Boolean weakVelocitySecret,
                            java.util.List<String> worldsWithoutAntiXray) {

    public static final SecurityFacts UNKNOWN = new SecurityFacts(null, null, null, null, null, null);

    /** Without anti-xray information. */
    public SecurityFacts(Boolean onlineMode, Boolean bungeeCordForwarding, Boolean boundToAllInterfaces,
                         Boolean velocityForwarding, Boolean weakVelocitySecret) {
        this(onlineMode, bungeeCordForwarding, boundToAllInterfaces, velocityForwarding, weakVelocitySecret, null);
    }

    /** Players arrive through a proxy that forwards their identity. */
    public boolean behindProxy() {
        return Boolean.TRUE.equals(bungeeCordForwarding) || Boolean.TRUE.equals(velocityForwarding);
    }
}
