package io.github.intruder_tech.blackdoor.core.platform;

/**
 * Security-relevant facts about the server, read by the platform adapter for /blackdoor setup.
 * {@code null} means "unknown on this platform".
 *
 * @param onlineMode           server authenticates players with Mojang itself
 * @param bungeeCordForwarding BungeeCord legacy IP/UUID forwarding is enabled (spigot.yml)
 * @param boundToAllInterfaces server listens on every network interface (empty server-ip)
 */
public record SecurityFacts(Boolean onlineMode, Boolean bungeeCordForwarding, Boolean boundToAllInterfaces) {

    public static final SecurityFacts UNKNOWN = new SecurityFacts(null, null, null);
}
