package io.github.intruder_tech.blackdoor.core.trace;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.packets.TraceListener;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.protocol.world.BlockSource;
import io.github.intruder_tech.blackdoor.state.PlayerSession;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/**
 * Records golden traces (milestone 2.4b) as gzip JSON lines in {@code traces/}: everything the
 * physics core needs to replay a player's movement. A recording is armed by name and starts when
 * the player next joins, because the server sends terrain, effects and attributes at join.
 * Bounded in duration and size; written off-thread.
 */
public final class TraceRecorder implements TraceListener {

    public static final int MAX_SECONDS = 300;
    static final int MAX_LINES = 200_000;
    private static final int FORMAT = 2;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private record Armed(int seconds, BdSender requester) {
    }

    private final BlackDoorEngine engine;
    private final Map<String, Armed> armed = new ConcurrentHashMap<>();
    private final Map<UUID, Recording> recordings = new ConcurrentHashMap<>();

    public TraceRecorder(BlackDoorEngine engine) {
        this.engine = engine;
    }

    static final class Recording {
        final String name;
        final long startNanos;
        final long endNanos;
        final int entityId;
        final BdSender requester;
        final List<String> lines = new ArrayList<>();
        boolean truncated;

        Recording(String name, long startNanos, long endNanos, int entityId, BdSender requester) {
            this.name = name;
            this.startNanos = startNanos;
            this.endNanos = endNanos;
            this.entityId = entityId;
            this.requester = requester;
        }

        synchronized void add(String line) {
            if (lines.size() < MAX_LINES) {
                lines.add(line);
            } else {
                truncated = true;
            }
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Waits for the player to join; returns false if they are already armed or being recorded. */
    public boolean arm(String player, int seconds, BdSender requester) {
        if (recordings.values().stream().anyMatch(r -> r.name.equalsIgnoreCase(player))) {
            return false;
        }
        return armed.putIfAbsent(key(player), new Armed(seconds, requester)) == null;
    }

    /** Cancels a waiting recording; returns false if there was none. */
    public boolean cancel(String player) {
        return armed.remove(key(player)) != null;
    }

    /** Stops and saves a running recording; returns false if there was none. */
    public boolean stop(String player) {
        for (Map.Entry<UUID, Recording> e : recordings.entrySet()) {
            if (e.getValue().name.equalsIgnoreCase(player)) {
                return stop(e.getKey());
            }
        }
        return false;
    }

    private boolean stop(UUID player) {
        Recording rec = recordings.remove(player);
        if (rec == null) {
            return false;
        }
        write(rec);
        return true;
    }

    @Override
    public boolean active() {
        return !armed.isEmpty() || !recordings.isEmpty();
    }

    @Override
    public void inbound(PlayerSession session, ServerboundPacket packet, long now) {
        Recording rec = recordingFor(session, now);
        if (rec == null) {
            return;
        }
        String t = "{\"t\":" + (now - rec.startNanos);
        if (packet instanceof ServerboundPacket.Movement m) {
            rec.add(t + ",\"k\":\"move\",\"x\":" + m.x() + ",\"y\":" + m.y() + ",\"z\":" + m.z()
                    + ",\"yaw\":" + m.yaw() + ",\"pitch\":" + m.pitch() + ",\"pos\":" + m.hasPosition()
                    + ",\"rot\":" + m.hasRotation() + ",\"ground\":" + m.onGround() + "}");
        } else if (packet instanceof ServerboundPacket.TickEnd) {
            rec.add(t + ",\"k\":\"tick\"}");
        } else if (packet instanceof ServerboundPacket.Input i) {
            rec.add(t + ",\"k\":\"input\",\"forward\":" + i.forward() + ",\"backward\":" + i.backward()
                    + ",\"left\":" + i.left() + ",\"right\":" + i.right() + ",\"jump\":" + i.jump()
                    + ",\"sneak\":" + i.sneak() + ",\"sprint\":" + i.sprint() + "}");
        } else if (packet instanceof ServerboundPacket.PlayerAction a) {
            rec.add(t + ",\"k\":\"action\",\"name\":\"" + token(a.action()) + "\"}");
        } else if (packet instanceof ServerboundPacket.Flying f) {
            rec.add(t + ",\"k\":\"flying\",\"flying\":" + f.flying() + "}");
        } else if (packet instanceof ServerboundPacket.TeleportConfirm c) {
            rec.add(t + ",\"k\":\"confirm\",\"id\":" + c.teleportId() + "}");
        }
    }

    @Override
    public void outbound(PlayerSession session, ClientboundPacket packet, long now) {
        if (packet instanceof ClientboundPacket.JoinGame join) {
            start(session, join.entityId(), now);
            return;
        }
        Recording rec = recordingFor(session, now);
        if (rec == null) {
            return;
        }
        String t = "{\"t\":" + (now - rec.startNanos);
        if (packet instanceof ClientboundPacket.Velocity v && v.entityId() == rec.entityId) {
            rec.add(t + ",\"k\":\"velocity\",\"x\":" + v.x() + ",\"y\":" + v.y() + ",\"z\":" + v.z() + "}");
        } else if (packet instanceof ClientboundPacket.SyncPosition s) {
            rec.add(t + ",\"k\":\"teleport\",\"id\":" + s.teleportId() + ",\"x\":" + s.x() + ",\"y\":" + s.y()
                    + ",\"z\":" + s.z() + ",\"yaw\":" + s.yaw() + ",\"pitch\":" + s.pitch()
                    + ",\"relative\":" + s.relative() + "}");
        } else if (packet instanceof ClientboundPacket.WorldChange w) {
            rec.add(t + ",\"k\":\"world\",\"name\":\"" + safe(w.worldName()) + "\"}");
        } else if (packet instanceof ClientboundPacket.BlockChange b) {
            rec.add(t + ",\"k\":\"block\",\"x\":" + b.x() + ",\"y\":" + b.y() + ",\"z\":" + b.z() + ",\"id\":" + b.blockStateId() + "}");
        } else if (packet instanceof ClientboundPacket.ChunkData c) {
            rec.add(t + ",\"k\":\"chunk\",\"x\":" + c.chunkX() + ",\"z\":" + c.chunkZ() + ",\"minSection\":"
                    + c.minSectionY() + ",\"sections\":" + sections(c.blocks()) + "}");
        } else if (packet instanceof ClientboundPacket.ChunkUnload u) {
            rec.add(t + ",\"k\":\"unload\",\"x\":" + u.chunkX() + ",\"z\":" + u.chunkZ() + "}");
        } else if (packet instanceof ClientboundPacket.GameModeChange g) {
            rec.add(t + ",\"k\":\"gamemode\",\"creative\":" + g.creative() + "}");
        } else if (packet instanceof ClientboundPacket.Effect e && e.entityId() == rec.entityId) {
            rec.add(t + ",\"k\":\"effect\",\"name\":\"" + token(e.effect()) + "\",\"amplifier\":" + e.amplifier()
                    + ",\"ticks\":" + e.durationTicks() + "}");
        } else if (packet instanceof ClientboundPacket.EffectRemoved e && e.entityId() == rec.entityId) {
            rec.add(t + ",\"k\":\"effectRemoved\",\"name\":\"" + token(e.effect()) + "\"}");
        } else if (packet instanceof ClientboundPacket.Attribute a && a.entityId() == rec.entityId) {
            StringBuilder modifiers = new StringBuilder("[");
            for (ClientboundPacket.Attribute.Modifier m : a.modifiers()) {
                modifiers.append(modifiers.length() > 1 ? "," : "").append('[').append(m.amount()).append(',')
                        .append(m.operation()).append(']');
            }
            rec.add(t + ",\"k\":\"attribute\",\"name\":\"" + token(a.key()) + "\",\"base\":" + a.base()
                    + ",\"modifiers\":" + modifiers.append(']') + "}");
        } else if (packet instanceof ClientboundPacket.Abilities a) {
            rec.add(t + ",\"k\":\"abilities\",\"flying\":" + a.flying() + ",\"allowed\":" + a.flightAllowed()
                    + ",\"speed\":" + a.flySpeed() + "}");
        }
    }

    @Override
    public void disconnected(PlayerSession session) {
        if (session.uniqueId() != null) {
            stop(session.uniqueId());
        }
    }

    private void start(PlayerSession session, int entityId, long now) {
        String name = session.name();
        if (name == null || session.uniqueId() == null || recordings.containsKey(session.uniqueId())) {
            return;
        }
        Armed waiting = armed.remove(key(name));
        if (waiting == null) {
            return;
        }
        Recording rec = new Recording(name, now, now + waiting.seconds() * 1_000_000_000L, entityId, waiting.requester());
        rec.add("{\"k\":\"meta\",\"v\":" + FORMAT + ",\"player\":\"" + safe(name) + "\",\"client\":\""
                + session.clientVersion() + "\",\"server\":\""
                + engine.platform().info().minecraft().map(Object::toString).orElse("?")
                + "\",\"entity\":" + entityId + ",\"seconds\":" + waiting.seconds() + "}");
        recordings.put(session.uniqueId(), rec);
        waiting.requester().sendMessage(Text.PREFIX + "&a" + Text.CHECK + " Recording &f" + safe(name) + " &afor &f"
                + waiting.seconds() + "s&a.");
    }

    private Recording recordingFor(PlayerSession session, long now) {
        if (recordings.isEmpty() || session.uniqueId() == null) {
            return null;
        }
        Recording rec = recordings.get(session.uniqueId());
        if (rec != null && now > rec.endNanos) {
            stop(session.uniqueId());
            return null;
        }
        return rec;
    }

    /** Non-empty sections as [index, [count, blockStateId, ...]], running x fastest, then z, then y. */
    private static String sections(BlockSource blocks) {
        StringBuilder out = new StringBuilder("[");
        for (int section = 0; section < blocks.sections(); section++) {
            if (blocks.emptySection(section)) {
                continue;
            }
            out.append(out.length() > 1 ? "," : "").append('[').append(section).append(",[");
            int current = blocks.blockId(section, 0, 0, 0);
            int count = 0;
            for (int i = 0; i < 4096; i++) {
                int id = blocks.blockId(section, i & 15, i >> 8, (i >> 4) & 15);
                if (id != current) {
                    out.append(count).append(',').append(current).append(',');
                    current = id;
                    count = 0;
                }
                count++;
            }
            out.append(count).append(',').append(current).append("]]");
        }
        return out.append(']').toString();
    }

    private void write(Recording rec) {
        String file = "trace-" + safe(rec.name) + "-" + STAMP.format(Instant.now()) + ".jsonl.gz";
        boolean queued = engine.submitWork(() -> {
            try {
                Path dir = engine.platform().dataDirectory().resolve("traces");
                Files.createDirectories(dir);
                List<String> copy;
                synchronized (rec) {
                    copy = new ArrayList<>(rec.lines);
                }
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        new GZIPOutputStream(Files.newOutputStream(dir.resolve(file))), StandardCharsets.UTF_8))) {
                    for (String line : copy) {
                        writer.write(line);
                        writer.write('\n');
                    }
                }
                engine.audit().ifPresent(a -> a.record(rec.requester.name(), "trace.record " + rec.name, copy.size() + " lines"));
                rec.requester.sendMessage(Text.PREFIX + "&a" + Text.CHECK + " Recording of &f" + safe(rec.name)
                        + "&a saved: &ftraces/" + file + " &7(" + (copy.size() - 1) + " events"
                        + (rec.truncated ? ", truncated" : "") + ")");
            } catch (IOException e) {
                rec.requester.sendMessage(Text.PREFIX + "&c" + Text.CROSS + " Could not save the recording: " + e.getClass().getSimpleName());
            }
        });
        if (!queued) {
            rec.requester.sendMessage(Text.PREFIX + "&cBlack Door is busy; the recording was discarded.");
        }
    }

    /** File- and JSON-safe player name (letters, digits, underscore). */
    static String safe(String name) {
        if (name == null) {
            return "unknown";
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9_]", "_");
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    /** JSON-safe protocol identifier such as {@code minecraft:speed}. */
    private static String token(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9_.:/-]", "_");
    }
}
