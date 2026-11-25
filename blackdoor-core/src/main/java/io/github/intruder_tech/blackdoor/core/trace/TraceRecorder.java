package io.github.intruder_tech.blackdoor.core.trace;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.packets.TraceListener;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.state.PlayerSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records golden traces (milestone 2.4b): a player's movement packets plus the server events that
 * affect movement, as JSON lines in {@code traces/}. Used to verify that physics prediction
 * produces zero flags on legitimate play. Bounded in duration and size; written off-thread.
 */
public final class TraceRecorder implements TraceListener {

    public static final int MAX_SECONDS = 300;
    static final int MAX_LINES = 200_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final BlackDoorEngine engine;
    private final Map<UUID, Recording> active = new ConcurrentHashMap<>();

    public TraceRecorder(BlackDoorEngine engine) {
        this.engine = engine;
    }

    static final class Recording {
        final String name;
        final long startNanos;
        final long endNanos;
        final BdSender requester;
        final List<String> lines = new ArrayList<>();
        boolean truncated;

        Recording(String name, long startNanos, long endNanos, BdSender requester) {
            this.name = name;
            this.startNanos = startNanos;
            this.endNanos = endNanos;
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

    /** Starts recording; returns false if the player is already being recorded. */
    public boolean start(PlayerSession session, int seconds, BdSender requester) {
        long now = engine.time().nanoTime();
        Recording rec = new Recording(session.name(), now, now + seconds * 1_000_000_000L, requester);
        rec.add("{\"k\":\"meta\",\"player\":\"" + safe(session.name()) + "\",\"client\":\"" + session.clientVersion()
                + "\",\"server\":\"" + engine.platform().info().minecraft().map(Object::toString).orElse("?")
                + "\",\"entity\":" + session.entityId() + ",\"seconds\":" + seconds + "}");
        return active.putIfAbsent(session.uniqueId(), rec) == null;
    }

    public boolean stop(UUID player) {
        Recording rec = active.remove(player);
        if (rec == null) {
            return false;
        }
        write(rec);
        return true;
    }

    public boolean isRecording(UUID player) {
        return active.containsKey(player);
    }

    @Override
    public void inbound(PlayerSession session, ServerboundPacket packet, long now) {
        Recording rec = recordingFor(session, now);
        if (rec != null && packet instanceof ServerboundPacket.Movement m) {
            rec.add("{\"t\":" + (now - rec.startNanos) + ",\"k\":\"move\",\"x\":" + m.x() + ",\"y\":" + m.y() + ",\"z\":" + m.z()
                    + ",\"yaw\":" + m.yaw() + ",\"pitch\":" + m.pitch() + ",\"pos\":" + m.hasPosition()
                    + ",\"rot\":" + m.hasRotation() + ",\"ground\":" + m.onGround() + "}");
        }
    }

    @Override
    public void outbound(PlayerSession session, ClientboundPacket packet, long now) {
        Recording rec = recordingFor(session, now);
        if (rec == null) {
            return;
        }
        String t = "{\"t\":" + (now - rec.startNanos);
        if (packet instanceof ClientboundPacket.Velocity v && v.entityId() == session.entityId()) {
            rec.add(t + ",\"k\":\"velocity\",\"x\":" + v.x() + ",\"y\":" + v.y() + ",\"z\":" + v.z() + "}");
        } else if (packet instanceof ClientboundPacket.SyncPosition s) {
            rec.add(t + ",\"k\":\"teleport\",\"id\":" + s.teleportId() + "}");
        } else if (packet instanceof ClientboundPacket.WorldChange w) {
            rec.add(t + ",\"k\":\"world\",\"name\":\"" + safe(w.worldName()) + "\"}");
        } else if (packet instanceof ClientboundPacket.BlockChange b) {
            rec.add(t + ",\"k\":\"block\",\"x\":" + b.x() + ",\"y\":" + b.y() + ",\"z\":" + b.z() + ",\"id\":" + b.blockStateId() + "}");
        }
    }

    @Override
    public void disconnected(PlayerSession session) {
        if (session.uniqueId() != null) {
            stop(session.uniqueId());
        }
    }

    private Recording recordingFor(PlayerSession session, long now) {
        if (active.isEmpty() || session.uniqueId() == null) {
            return null;
        }
        Recording rec = active.get(session.uniqueId());
        if (rec != null && now > rec.endNanos) {
            stop(session.uniqueId());
            return null;
        }
        return rec;
    }

    private void write(Recording rec) {
        String file = "trace-" + safe(rec.name) + "-" + STAMP.format(Instant.now()) + ".jsonl";
        boolean queued = engine.submitWork(() -> {
            try {
                Path dir = engine.platform().dataDirectory().resolve("traces");
                Files.createDirectories(dir);
                List<String> copy;
                synchronized (rec) {
                    copy = new ArrayList<>(rec.lines);
                }
                Files.write(dir.resolve(file), copy, StandardCharsets.UTF_8);
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
}
