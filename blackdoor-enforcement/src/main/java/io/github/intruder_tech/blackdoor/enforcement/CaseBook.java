package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.common.text.LogSanitizer;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.violations.Finding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What Black Door did to players and why, for staff to review. The newest {@value #MAX_CASES} cases,
 * kept in Black Door's own folder. No addresses are stored. Thread-safe and bounded.
 * {@link #save()} and the constructor do file work.
 */
public final class CaseBook {

    public static final int MAX_CASES = 200;
    private static final long REPEAT_MILLIS = 10 * 60_000L;

    public enum Action {
        WOULD_KICK,
        QUARANTINE,
        KICK,
        BAN
    }

    public record Evidence(long timeMillis, String checkId, String detail, boolean blocked) {
    }

    public record Case(int id, long timeMillis, UUID player, String name, Action action, boolean falseAlarm,
                       List<Evidence> evidence) {

        private Case with(boolean newFalseAlarm) {
            return new Case(id, timeMillis, player, name, action, newFalseAlarm, evidence);
        }
    }

    private final Path file;
    private final TimeSource time;
    private final Object writing = new Object();
    private final TreeMap<Integer, Case> cases = new TreeMap<>();
    private int unreadableLines;

    public CaseBook(Path file, TimeSource time) {
        this.file = Objects.requireNonNull(file, "file");
        this.time = Objects.requireNonNull(time, "time");
        load();
    }

    public synchronized Case open(UUID player, String name, Action action, List<Finding> findings) {
        List<Evidence> evidence = findings.stream()
                .map(f -> new Evidence(f.timeMillis(), clean(f.checkId(), 64), clean(f.detail(), 160), f.mitigated()))
                .toList();
        if (action == Action.WOULD_KICK) {
            // Watch mode decides again every few findings; one case per spell is enough to review.
            long recent = time.currentTimeMillis() - REPEAT_MILLIS;
            Optional<Case> same = cases.descendingMap().values().stream()
                    .filter(c -> c.action() == action && c.player().equals(player) && c.timeMillis() >= recent)
                    .findFirst();
            if (same.isPresent()) {
                return same.get();
            }
        }
        int id = cases.isEmpty() ? 1 : cases.lastKey() + 1;
        Case opened = new Case(id, time.currentTimeMillis(), player, clean(name, 32), action, false, evidence);
        cases.put(id, opened);
        trim();
        return opened;
    }

    public synchronized List<Case> newest(int limit) {
        return cases.descendingMap().values().stream().limit(limit).toList();
    }

    public synchronized Optional<Case> find(int id) {
        return Optional.ofNullable(cases.get(id));
    }

    /** Records the staff verdict; empty if there is no such case. */
    public synchronized Optional<Case> markFalseAlarm(int id, boolean falseAlarm) {
        return Optional.ofNullable(cases.computeIfPresent(id, (key, old) -> old.with(falseAlarm)));
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
        for (Case c : cases.values()) {
            line(out, "case", c.id(), c.timeMillis(), c.player(), c.action(), c.falseAlarm(), c.name());
            for (Evidence e : c.evidence()) {
                line(out, "evidence", c.id(), e.timeMillis(), e.checkId(), e.blocked(), e.detail());
            }
        }
        return out.toString();
    }

    private static void line(StringBuilder out, Object... parts) {
        for (int i = 0; i < parts.length; i++) {
            out.append(i == 0 ? "" : "\t").append(parts[i]);
        }
        out.append('\n');
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
            if (!read(line)) {
                unreadableLines++;
            }
        }
        trim();
    }

    private boolean read(String line) {
        try {
            if (line.startsWith("case\t")) {
                String[] p = line.split("\t", 7);
                int id = Integer.parseInt(p[1]);
                cases.put(id, new Case(id, Long.parseLong(p[2]), UUID.fromString(p[3]), clean(p[6], 32),
                        Action.valueOf(p[4]), Boolean.parseBoolean(p[5]), List.of()));
                return true;
            }
            if (line.startsWith("evidence\t")) {
                String[] p = line.split("\t", 6);
                Case owner = cases.get(Integer.parseInt(p[1]));
                if (owner == null) {
                    return false;
                }
                List<Evidence> evidence = new ArrayList<>(owner.evidence());
                evidence.add(new Evidence(Long.parseLong(p[2]), clean(p[3], 64), clean(p[5], 160),
                        Boolean.parseBoolean(p[4])));
                cases.put(owner.id(), new Case(owner.id(), owner.timeMillis(), owner.player(), owner.name(),
                        owner.action(), owner.falseAlarm(), List.copyOf(evidence)));
                return true;
            }
            return false;
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    private void trim() {
        while (cases.size() > MAX_CASES) {
            cases.pollFirstEntry();
        }
    }

    private static String clean(String value, int maxLength) {
        return LogSanitizer.sanitize(value, maxLength).replace('\t', ' ');
    }
}
