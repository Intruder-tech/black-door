package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.config.ConfigReader;

import java.util.EnumMap;
import java.util.Map;

/** Parsed {@code checks.yml} section {@code packet}. Immutable. */
public record PacketSettings(Map<PacketCheck, CheckMode> modes, int maxMovementBytes, int maxChatLength,
                             int maxCommandLength, int maxTabCompleteLength, Rate movementRate, Rate interactRate,
                             Rate textRate, Rate totalRate, int maxSessions, ExploitLimits exploits) {

    /** Limits of the exploit guards (docs/EXPLOIT_PREVENTION.md). Operators can raise them. */
    public record ExploitLimits(int payloadBytes, int channelLength, int brandBytes, int bookPages, int pageLength,
                                int titleLength, int signLineLength, int creativeAmount, int creativeBytes,
                                int itemDepth, int itemElements) {

        static final ExploitLimits DEFAULTS =
                new ExploitLimits(32767, 128, 256, 100, 8192, 32, 384, 99, 65536, 32, 8192);

        private static ExploitLimits parse(ConfigReader r) {
            ExploitLimits d = DEFAULTS;
            return new ExploitLimits(
                    r.getInt("packet.payload.max-bytes", d.payloadBytes(), 256, 1048576),
                    r.getInt("packet.payload.max-channel-length", d.channelLength(), 20, 32767),
                    r.getInt("packet.payload.max-brand-bytes", d.brandBytes(), 16, 32767),
                    r.getInt("packet.book.max-pages", d.bookPages(), 1, 1000),
                    r.getInt("packet.book.max-page-length", d.pageLength(), 256, 32767),
                    r.getInt("packet.book.max-title-length", d.titleLength(), 16, 1024),
                    r.getInt("packet.sign.max-line-length", d.signLineLength(), 15, 32767),
                    r.getInt("packet.creative-item.max-amount", d.creativeAmount(), 1, 127),
                    r.getInt("packet.creative-item.max-bytes", d.creativeBytes(), 1024, 2097152),
                    r.getInt("packet.item-data.max-depth", d.itemDepth(), 8, 512),
                    r.getInt("packet.item-data.max-elements", d.itemElements(), 256, 99999));
        }
    }

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
                new Rate(50, 20), new Rate(1000, 500), 5000, ExploitLimits.DEFAULTS);
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
                r.getInt("packet.max-sessions", d.maxSessions(), 10, 100000),
                ExploitLimits.parse(r));
    }

    private static Rate rate(ConfigReader r, String path, Rate d) {
        return new Rate(r.getInt(path + ".burst", d.burst(), 1, 100000),
                r.getInt(path + ".per-second", (int) d.perSecond(), 1, 100000));
    }
}
