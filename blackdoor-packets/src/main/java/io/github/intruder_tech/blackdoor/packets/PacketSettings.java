package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.config.ConfigReader;

import java.util.EnumMap;
import java.util.Map;

/** Parsed {@code checks.yml} section {@code packet}. Immutable. */
public record PacketSettings(Map<PacketCheck, CheckMode> modes, int maxMovementBytes, int maxChatLength,
                             int maxCommandLength, int maxTabCompleteLength, Rate movementRate, Rate interactRate,
                             Rate textRate, Rate totalRate, int maxSessions) {

    public PacketSettings {
        modes = Map.copyOf(modes);
    }

    public CheckMode mode(PacketCheck check) {
        return modes.getOrDefault(check, CheckMode.MONITOR);
    }

    public static PacketSettings defaults() {
        Map<PacketCheck, CheckMode> modes = new EnumMap<>(PacketCheck.class);
        for (PacketCheck c : PacketCheck.values()) {
            modes.put(c, CheckMode.MONITOR);
        }
        return new PacketSettings(modes, 64, 256, 32767, 32500, new Rate(200, 40), new Rate(60, 40),
                new Rate(50, 20), new Rate(1000, 500), 5000);
    }

    public static PacketSettings parse(ConfigReader r) {
        PacketSettings d = defaults();
        Map<PacketCheck, CheckMode> modes = new EnumMap<>(PacketCheck.class);
        for (PacketCheck c : PacketCheck.values()) {
            modes.put(c, r.getEnum("packet." + c.configKey() + ".mode", CheckMode.class, d.mode(c)));
        }
        return new PacketSettings(modes,
                r.getInt("packet.oversized.max-movement-bytes", d.maxMovementBytes(), 34, 4096),
                r.getInt("packet.oversized.max-chat-length", d.maxChatLength(), 1, 32767),
                r.getInt("packet.oversized.max-command-length", d.maxCommandLength(), 1, 32767),
                r.getInt("packet.oversized.max-tab-complete-length", d.maxTabCompleteLength(), 1, 32767),
                rate(r, "packet.flood.movement", d.movementRate()),
                rate(r, "packet.flood.interact", d.interactRate()),
                rate(r, "packet.flood.text", d.textRate()),
                rate(r, "packet.flood.total", d.totalRate()),
                r.getInt("packet.max-sessions", d.maxSessions(), 10, 100000));
    }

    private static Rate rate(ConfigReader r, String path, Rate d) {
        return new Rate(r.getInt(path + ".burst", d.burst(), 1, 100000),
                r.getInt(path + ".per-second", (int) d.perSecond(), 1, 100000));
    }
}
