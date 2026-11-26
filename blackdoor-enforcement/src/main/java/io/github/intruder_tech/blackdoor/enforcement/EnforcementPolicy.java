package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.violations.Confidence;
import io.github.intruder_tech.blackdoor.violations.Finding;
import io.github.intruder_tech.blackdoor.violations.Subject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Counts a player's recent findings and says when they justify a kick. Only deterministic and
 * high-confidence findings count (docs/FALSE_POSITIVE_POLICY.md), and only blocked ones can cause a
 * kick, so a check set to Watch never punishes anyone. Decides only; the caller acts. Thread-safe
 * and bounded.
 */
public final class EnforcementPolicy {

    /**
     * @param kick     false when the player would have been kicked but the mode only watches
     * @param evidence the latest findings that counted, oldest first, at most {@value #MAX_EVIDENCE}
     */
    public record Decision(Subject subject, String checkId, int findings, boolean kick, List<Finding> evidence) {
    }

    private static final int MAX_EVIDENCE = 10;

    private final Supplier<EnforcementSettings> settings;
    private final Map<UUID, Deque<Finding>> players;

    public EnforcementPolicy(Supplier<EnforcementSettings> settings, int maxPlayers) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.players = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Deque<Finding>> eldest) {
                return size() > maxPlayers;
            }
        };
    }

    public synchronized Optional<Decision> accept(Finding finding) {
        EnforcementSettings current = settings.get();
        UUID id = finding.subject().uniqueId();
        boolean sure = finding.confidence() == Confidence.DETERMINISTIC || finding.confidence() == Confidence.HIGH;
        if (current.mode() == EnforcementSettings.Mode.DISABLED || id == null || !sure) {
            return Optional.empty();
        }
        // "kick" here means act on the player: kick or quarantine, as the mode says.
        boolean kick = current.mode() != EnforcementSettings.Mode.WATCH;
        Deque<Finding> seen = players.computeIfAbsent(id, key -> new ArrayDeque<>());
        seen.addLast(finding);
        long oldest = finding.timeMillis() - current.windowSeconds() * 1000L;
        // The size limit keeps one flooding player's history bounded.
        while (!seen.isEmpty()
                && (seen.peekFirst().timeMillis() < oldest || seen.size() > current.findingsToKick() * 2)) {
            seen.removeFirst();
        }
        List<Finding> counted = seen.stream().filter(f -> f.mitigated() || !kick).toList();
        if (counted.size() < current.findingsToKick()) {
            return Optional.empty();
        }
        players.remove(id);
        List<Finding> latest = List.copyOf(counted.subList(Math.max(0, counted.size() - MAX_EVIDENCE),
                counted.size()));
        return Optional.of(new Decision(finding.subject(), finding.checkId(), counted.size(), kick, latest));
    }
}
