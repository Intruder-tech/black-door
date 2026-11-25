package io.github.intruder_tech.blackdoor.violations;

import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.common.text.LogSanitizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Phase 2 ledger: counts findings per check and per subject, keeps a short history of recent
 * findings, and writes rate-limited alerts to the log. Every structure is bounded (SR-10).
 * No decay, correlation or enforcement yet (Phase 5).
 */
public final class ViolationLedger implements FindingSink {

    static final int MAX_CHECKS = 256;

    private final RateLimitedLogger alerts;
    private final int maxSubjects;
    private final int recentCapacity;
    private final Map<String, LongAdder> perCheck = new ConcurrentHashMap<>();
    private final Map<String, SubjectRecord> perSubject;
    private final Deque<Finding> recent = new ArrayDeque<>();
    private final LongAdder total = new LongAdder();

    public ViolationLedger(RateLimitedLogger alerts, int maxSubjects, int recentCapacity) {
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        if (maxSubjects < 1 || recentCapacity < 1) {
            throw new IllegalArgumentException("capacities must be >= 1");
        }
        this.maxSubjects = maxSubjects;
        this.recentCapacity = recentCapacity;
        this.perSubject = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SubjectRecord> eldest) {
                return size() > ViolationLedger.this.maxSubjects;
            }
        };
    }

    @Override
    public void accept(Finding finding) {
        LongAdder counter = perCheck.get(finding.checkId());
        if (counter == null) {
            if (perCheck.size() >= MAX_CHECKS) {
                return;
            }
            counter = perCheck.computeIfAbsent(finding.checkId(), id -> new LongAdder());
        }
        counter.increment();
        total.increment();
        String key = subjectKey(finding.subject());
        synchronized (this) {
            perSubject.computeIfAbsent(key, k -> new SubjectRecord()).add(finding);
            recent.addLast(finding);
            if (recent.size() > recentCapacity) {
                recent.removeFirst();
            }
        }
        alerts.warn(finding.checkId(), "[alert] " + finding.checkId() + " "
                + LogSanitizer.sanitize(finding.subject().displayName(), 32)
                + " - " + LogSanitizer.sanitize(finding.detail(), 160)
                + (finding.mitigated() ? " (blocked)" : " (monitor)"));
    }

    public long total() {
        return total.sum();
    }

    /** Findings per check id, sorted by id. */
    public Map<String, Long> countsByCheck() {
        Map<String, Long> out = new TreeMap<>();
        perCheck.forEach((id, adder) -> out.put(id, adder.sum()));
        return out;
    }

    /** Findings for one subject, per check. Empty if none. */
    public synchronized Map<String, Long> countsForSubject(Subject subject) {
        SubjectRecord record = perSubject.get(subjectKey(subject));
        return record == null ? Map.of() : new TreeMap<>(record.counts);
    }

    /** The most recent findings, oldest first. */
    public synchronized List<Finding> recent(int limit) {
        List<Finding> all = new ArrayList<>(recent);
        return List.copyOf(all.subList(Math.max(0, all.size() - limit), all.size()));
    }

    public synchronized int trackedSubjects() {
        return perSubject.size();
    }

    private static String subjectKey(Subject subject) {
        if (subject.uniqueId() != null) {
            return subject.uniqueId().toString();
        }
        return subject.name() != null ? "name:" + subject.name() : "unknown";
    }

    private static final class SubjectRecord {
        final Map<String, Long> counts = new LinkedHashMap<>();

        void add(Finding finding) {
            if (counts.size() < MAX_CHECKS || counts.containsKey(finding.checkId())) {
                counts.merge(finding.checkId(), 1L, Long::sum);
            }
        }
    }
}
