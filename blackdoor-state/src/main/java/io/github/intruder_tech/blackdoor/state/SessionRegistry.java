package io.github.intruder_tech.blackdoor.state;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * All live sessions, keyed by connection. Bounded: when {@code maxSessions} is reached new
 * connections are not tracked (counted as rejected) rather than growing memory (SR-10).
 */
public final class SessionRegistry {

    private final Map<Object, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final int maxSessions;
    private final TimeSource time;
    private final AtomicLong rejected = new AtomicLong();

    public SessionRegistry(int maxSessions, TimeSource time) {
        if (maxSessions < 1) {
            throw new IllegalArgumentException("maxSessions must be >= 1");
        }
        this.maxSessions = maxSessions;
        this.time = Objects.requireNonNull(time, "time");
    }

    /** Session for the connection, created on first sight. Empty when at capacity. */
    public Optional<PlayerSession> session(ConnectionInfo info) {
        PlayerSession session = sessions.get(info.key());
        if (session == null) {
            if (sessions.size() >= maxSessions) {
                rejected.incrementAndGet();
                return Optional.empty();
            }
            session = sessions.computeIfAbsent(info.key(), k -> new PlayerSession(k, time.currentTimeMillis()));
        }
        session.update(info);
        return Optional.of(session);
    }

    /** Removes and returns the session for a closed connection. */
    public Optional<PlayerSession> remove(Object connectionKey) {
        return Optional.ofNullable(sessions.remove(connectionKey));
    }

    /** Live session by exact name (case-insensitive) or UUID string; for diagnostics only. */
    public Optional<PlayerSession> findSession(String nameOrUuid) {
        String wanted = nameOrUuid.toLowerCase(Locale.ROOT);
        for (PlayerSession s : sessions.values()) {
            if ((s.name() != null && s.name().toLowerCase(Locale.ROOT).equals(wanted))
                    || (s.uniqueId() != null && s.uniqueId().toString().equals(wanted))) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return sessions.size();
    }

    public long rejected() {
        return rejected.get();
    }

    /** Finds a session by exact player name (case-insensitive) or UUID string. */
    public Optional<SessionSnapshot> find(String nameOrUuid) {
        String wanted = nameOrUuid.toLowerCase(Locale.ROOT);
        long now = time.currentTimeMillis();
        for (PlayerSession s : sessions.values()) {
            if ((s.name() != null && s.name().toLowerCase(Locale.ROOT).equals(wanted))
                    || (s.uniqueId() != null && s.uniqueId().toString().equals(wanted))) {
                return Optional.of(s.snapshot(now));
            }
        }
        return Optional.empty();
    }

    /** Visits every live session (used by the transaction heartbeat). Weakly consistent. */
    public void forEach(java.util.function.Consumer<PlayerSession> action) {
        sessions.values().forEach(action);
    }

    /** Names of sessions that have completed login, for tab completion. */
    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (PlayerSession s : sessions.values()) {
            if (s.name() != null) {
                names.add(s.name());
            }
        }
        return names;
    }
}
