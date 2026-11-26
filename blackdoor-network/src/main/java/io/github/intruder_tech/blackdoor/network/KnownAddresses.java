package io.github.intruder_tech.blackdoor.network;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Addresses a real player has used: one from which somebody stayed in game for at least a minute.
 * Kept in memory only; the least recently seen address is forgotten first. Thread-safe.
 */
final class KnownAddresses {

    private static final long MIN_STAY_NANOS = 60_000_000_000L;

    private record Online(InetAddress address, long since) {
    }

    private final TimeSource time;
    private final Map<UUID, Online> online = new HashMap<>();
    private final Map<InetAddress, Boolean> remembered = new LinkedHashMap<>(64, 0.75f, true);

    KnownAddresses(TimeSource time) {
        this.time = time;
    }

    synchronized void joined(UUID id, InetAddress address) {
        if (id != null && address != null) {
            online.put(id, new Online(address, time.nanoTime()));
        }
    }

    /** A leave without a recorded join changes nothing. */
    synchronized void left(UUID id, int maxKnown) {
        Online player = id == null ? null : online.remove(id);
        if (player == null || !stayed(player)) {
            return;
        }
        remembered.put(player.address(), Boolean.TRUE);
        while (remembered.size() > maxKnown) {
            var eldest = remembered.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    synchronized boolean isKnown(InetAddress address) {
        if (remembered.get(address) != null) {
            return true;
        }
        // Linear in players online, which the server already bounds.
        return online.values().stream().anyMatch(p -> p.address().equals(address) && stayed(p));
    }

    synchronized int size() {
        return remembered.size();
    }

    private boolean stayed(Online player) {
        return time.nanoTime() - player.since() >= MIN_STAY_NANOS;
    }
}
