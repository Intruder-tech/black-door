package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.common.text.LogSanitizer;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Who was kicked recently and who is temporarily banned, kept in Black Door's own folder so it
 * survives a restart. Thread-safe and bounded. {@link #save()} and the constructor do file work.
 */
public final class BanBook {

    public record Ban(UUID id, String name, long untilMillis) {
    }

    private static final int MAX_PLAYERS = 10_000;

    private final Path file;
    private final TimeSource time;
    private final Object writing = new Object();
    private final Map<UUID, List<Long>> kicks = bounded();
    private final Map<UUID, Ban> bans = bounded();
    private int unreadableLines;

    public BanBook(Path file, TimeSource time) {
        this.file = Objects.requireNonNull(file, "file");
        this.time = Objects.requireNonNull(time, "time");
        load();
    }

    /** A length of time as shown to players and staff, rounded up. */
    public static String words(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        if (minutes >= 2880 || minutes == 1440) {
            return count((minutes + 1439) / 1440, "day");
        }
        if (minutes >= 120 || minutes == 60) {
            return count((minutes + 59) / 60, "hour");
        }
        return count(minutes, "minute");
    }

    private static String count(long number, String unit) {
        return number + " " + unit + (number == 1 ? "" : "s");
    }

    /** Whether one more kick would ban this player. */
    public synchronized boolean nextKickBans(UUID id, EnforcementSettings rules) {
        return rules.banMinutes() > 0 && recentKicks(id, rules).size() + 1 >= rules.banAfterKicks();
    }

    /** Counts a kick that happened; returns the ban it led to, if any. */
    public synchronized Optional<Ban> recordKick(UUID id, String name, EnforcementSettings rules) {
        boolean banNow = nextKickBans(id, rules);
        List<Long> recent = recentKicks(id, rules);
        recent.add(time.currentTimeMillis());
        if (!banNow) {
            while (recent.size() > 100) {
                recent.remove(0);
            }
            kicks.put(id, recent);
            return Optional.empty();
        }
        kicks.remove(id);
        Ban ban = new Ban(id, clean(name),
                time.currentTimeMillis() + Duration.ofMinutes(rules.banMinutes()).toMillis());
        bans.put(id, ban);
        return Optional.of(ban);
    }

    /** The ban this player is under right now. */
    public synchronized Optional<Ban> active(UUID id) {
        Ban ban = bans.get(id);
        if (ban == null || ban.untilMillis() <= time.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(ban);
    }

    /** All running bans, the one ending first first. */
    public synchronized List<Ban> active() {
        long now = time.currentTimeMillis();
        bans.values().removeIf(b -> b.untilMillis() <= now);
        return bans.values().stream().sorted(Comparator.comparingLong(Ban::untilMillis)).toList();
    }

    /** Ends the ban for a name (any capitals) and forgets that player's earlier kicks. */
    public synchronized boolean lift(String name) {
        List<UUID> found = active().stream().filter(b -> b.name().equalsIgnoreCase(name)).map(Ban::id).toList();
        found.forEach(id -> {
            bans.remove(id);
            kicks.remove(id);
        });
        return !found.isEmpty();
    }

    /** Drops this player's ban and kick count. */
    public synchronized void forget(UUID id) {
        bans.remove(id);
        kicks.remove(id);
    }

    public synchronized int unreadableLines() {
        return unreadableLines;
    }

    public void save() throws IOException {
        // One writer at a time, so an older copy can never replace a newer one.
        synchronized (writing) {
            String content = content();
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private synchronized String content() {
        StringBuilder out = new StringBuilder();
        kicks.forEach((id, times) -> times.forEach(at ->
                out.append("kick\t").append(id).append('\t').append(at).append('\n')));
        active().forEach(b -> out.append("ban\t").append(b.id()).append('\t').append(b.untilMillis())
                .append('\t').append(b.name()).append('\n'));
        return out.toString();
    }

    private List<Long> recentKicks(UUID id, EnforcementSettings rules) {
        long oldest = time.currentTimeMillis() - Duration.ofHours(rules.kickMemoryHours()).toMillis();
        List<Long> recent = new ArrayList<>(kicks.getOrDefault(id, List.of()));
        recent.removeIf(at -> at < oldest);
        return recent;
    }

    private synchronized void load() {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return;
        } catch (IOException e) {
            unreadableLines++;
            return;
        }
        for (String line : lines) {
            String[] part = line.split("\t", 4);
            try {
                UUID id = UUID.fromString(part[1]);
                long millis = Long.parseLong(part[2]);
                if (part[0].equals("kick") && part.length == 3) {
                    kicks.computeIfAbsent(id, key -> new ArrayList<>()).add(millis);
                } else if (part[0].equals("ban") && part.length == 4) {
                    bans.put(id, new Ban(id, clean(part[3]), millis));
                } else {
                    unreadableLines++;
                }
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                unreadableLines++;
            }
        }
    }

    private static String clean(String name) {
        return LogSanitizer.sanitize(name, 32).replace('\t', ' ');
    }

    private static <V> Map<UUID, V> bounded() {
        return new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, V> eldest) {
                return size() > MAX_PLAYERS;
            }
        };
    }
}
