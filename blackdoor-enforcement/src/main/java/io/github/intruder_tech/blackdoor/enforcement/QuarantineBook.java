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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players held in quarantine, kept in Black Door's own folder so leaving or a restart does not free
 * them. {@link #holds(UUID)} is cheap enough for every game event. Thread-safe and bounded.
 * {@link #save()} and the constructor do file work.
 */
public final class QuarantineBook {

    /** @param untilMillis when it ends by itself; 0 for only when staff release */
    public record Held(UUID id, String name, long untilMillis) {
    }

    private static final int MAX_PLAYERS = 1_000;

    private final Path file;
    private final TimeSource time;
    private final Object writing = new Object();
    private final Map<UUID, Held> held = new ConcurrentHashMap<>();
    private volatile int unreadableLines;

    public QuarantineBook(Path file, TimeSource time) {
        this.file = Objects.requireNonNull(file, "file");
        this.time = Objects.requireNonNull(time, "time");
        load();
    }

    /** Returns false if the player is already held, or the list is full. */
    public synchronized boolean add(UUID id, String name, int minutes) {
        if (holds(id) || held.size() >= MAX_PLAYERS) {
            return false;
        }
        long until = minutes == 0 ? 0 : time.currentTimeMillis() + Duration.ofMinutes(minutes).toMillis();
        held.put(id, new Held(id, clean(name), until));
        return true;
    }

    public boolean holds(UUID id) {
        Held found = held.get(id);
        return found != null && running(found);
    }

    public synchronized List<Held> active() {
        held.values().removeIf(h -> !running(h));
        return held.values().stream().sorted(Comparator.comparing(Held::name)).toList();
    }

    /** Frees the player with this name (any capitals). */
    public synchronized boolean release(String name) {
        return held.values().removeIf(h -> running(h) && h.name().equalsIgnoreCase(name));
    }

    public synchronized boolean release(UUID id) {
        return held.remove(id) != null;
    }

    public int unreadableLines() {
        return unreadableLines;
    }

    public void save() throws IOException {
        // One writer at a time, so an older copy can never replace a newer one.
        synchronized (writing) {
            StringBuilder content = new StringBuilder();
            active().forEach(h -> content.append(h.id()).append('\t').append(h.untilMillis()).append('\t')
                    .append(h.name()).append('\n'));
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

    private boolean running(Held h) {
        return h.untilMillis() == 0 || h.untilMillis() > time.currentTimeMillis();
    }

    private void load() {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return;
        } catch (IOException e) {
            unreadableLines = 1;
            return;
        }
        int bad = 0;
        for (String line : lines) {
            String[] part = line.split("\t", 3);
            try {
                UUID id = UUID.fromString(part[0]);
                held.put(id, new Held(id, clean(part[2]), Long.parseLong(part[1])));
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                bad++;
            }
        }
        unreadableLines = bad;
    }

    private static String clean(String name) {
        return LogSanitizer.sanitize(name, 32).replace('\t', ' ');
    }
}
