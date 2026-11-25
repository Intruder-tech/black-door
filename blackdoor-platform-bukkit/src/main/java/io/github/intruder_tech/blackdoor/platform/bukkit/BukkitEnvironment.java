package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.core.platform.PlatformInfo;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.Optional;

/** Detects which Bukkit-family server is running. Uses class presence only; never initializes server classes. */
final class BukkitEnvironment {

    private BukkitEnvironment() {
    }

    static PlatformInfo detect(Server server, ProtocolBridge protocol) {
        MinecraftVersion version = MinecraftVersion.parse(server.getBukkitVersion())
                .or(() -> fromVersionString(server.getVersion()))
                .or(protocol::serverVersion)
                .orElseThrow(() -> new IllegalStateException("Cannot determine Minecraft version from '"
                        + server.getBukkitVersion() + "'"));
        return new PlatformInfo(detectType(), server.getName(), server.getVersion(), version);
    }

    static PlatformType detectType() {
        if (classExists("io.papermc.paper.threadedregions.RegionizedServer")) {
            return PlatformType.FOLIA;
        }
        if (classExists("org.purpurmc.purpur.PurpurConfig")) {
            return PlatformType.PURPUR;
        }
        if (classExists("io.papermc.paper.configuration.Configuration") || classExists("com.destroystokyo.paper.PaperConfig")) {
            return PlatformType.PAPER;
        }
        // Legacy PaperSpigot (1.8.x) used a different package.
        if (classExists("org.github.paperspigot.PaperSpigotConfig")) {
            return PlatformType.PAPER;
        }
        if (classExists("org.spigotmc.SpigotConfig")) {
            return PlatformType.SPIGOT;
        }
        return PlatformType.BUKKIT;
    }

    /** Reads online-mode, BungeeCord forwarding (spigot.yml) and bind address; unknown values stay null. */
    static io.github.intruder_tech.blackdoor.core.platform.SecurityFacts securityFacts() {
        Boolean online = null;
        Boolean bungee = null;
        Boolean allInterfaces = null;
        try {
            online = Bukkit.getOnlineMode();
            String ip = Bukkit.getIp();
            allInterfaces = ip == null || ip.isEmpty();
        } catch (RuntimeException | LinkageError e) {
            // leave unknown
        }
        try {
            bungee = Bukkit.spigot().getConfig().getBoolean("settings.bungeecord", false);
        } catch (RuntimeException | LinkageError e) {
            // spigot.yml API not available on this platform
        }
        return new io.github.intruder_tech.blackdoor.core.platform.SecurityFacts(online, bungee, allInterfaces);
    }

    /** Paper 1.20+ and Folia expose region/entity schedulers on {@link Bukkit}. */
    static boolean hasRegionSchedulers() {
        try {
            Bukkit.class.getMethod("getGlobalRegionScheduler");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** Parses the "(MC: 1.8.8)" suffix of {@code Server#getVersion()}. */
    private static Optional<MinecraftVersion> fromVersionString(String version) {
        int index = version == null ? -1 : version.indexOf("MC: ");
        return index < 0 ? Optional.empty() : MinecraftVersion.parse(version.substring(index + 4));
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, BukkitEnvironment.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
