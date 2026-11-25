package io.github.intruder_tech.blackdoor.core.audit;

import io.github.intruder_tech.blackdoor.common.concurrent.BoundedExecutor;
import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.common.text.LogSanitizer;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Append-only audit trail of administrative actions (SR-71). One JSON object per line in
 * {@code audit/audit-YYYY-MM.log} (UTC). Writes happen on the worker executor; if the executor is
 * saturated the entry is counted as dropped and a warning is logged instead of blocking.
 */
public final class AuditLog {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private static final char BACKSLASH = (char) 92;

    private final Path directory;
    private final BoundedExecutor io;
    private final TimeSource time;
    private final BdLogger logger;
    private final boolean enabled;
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    public AuditLog(Path directory, BoundedExecutor io, TimeSource time, BdLogger logger, boolean enabled) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.io = Objects.requireNonNull(io, "io");
        this.time = Objects.requireNonNull(time, "time");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.enabled = enabled;
    }

    /**
     * Records an action. All values are sanitized; never pass secrets.
     *
     * @param actor  who did it (player name or "console")
     * @param action dotted action id, e.g. {@code config.reload}
     * @param result short outcome, e.g. {@code success}
     */
    public void record(String actor, String action, String result) {
        if (!enabled) {
            return;
        }
        long now = time.currentTimeMillis();
        String line = "{\"ts\":\"" + Instant.ofEpochMilli(now) + "\""
                + ",\"actor\":\"" + json(actor) + "\""
                + ",\"action\":\"" + json(action) + "\""
                + ",\"result\":\"" + json(result) + "\"}" + System.lineSeparator();
        Path file = directory.resolve("audit-" + MONTH.format(Instant.ofEpochMilli(now)) + ".log");
        if (!io.trySubmit(() -> append(file, line))) {
            dropped.incrementAndGet();
            logger.warn("Audit entry dropped (worker queue full): " + LogSanitizer.sanitize(action));
        }
    }

    public long written() {
        return written.get();
    }

    public long dropped() {
        return dropped.get();
    }

    private void append(Path file, String line) {
        try {
            Files.createDirectories(directory);
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            written.incrementAndGet();
        } catch (IOException e) {
            dropped.incrementAndGet();
            logger.warn("Cannot write audit log: " + e.getClass().getSimpleName());
        }
    }

    /** Sanitizes (control characters, length) then escapes JSON string specials. */
    static String json(String value) {
        String clean = LogSanitizer.sanitize(value, 200);
        StringBuilder out = new StringBuilder(clean.length() + 8);
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == '"' || c == BACKSLASH) {
                out.append(BACKSLASH);
            }
            out.append(c);
        }
        return out.toString();
    }
}
