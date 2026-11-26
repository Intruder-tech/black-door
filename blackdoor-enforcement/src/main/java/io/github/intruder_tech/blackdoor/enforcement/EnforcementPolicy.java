package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.violations.Confidence;
import io.github.intruder_tech.blackdoor.violations.Finding;
import io.github.intruder_tech.blackdoor.violations.Subject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
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

    /** @param kick false when the player would have been kicked but the mode only watches */
    public record Decision(Subject subject, String checkId, int findings, boolean kick) {
    }

    private record Seen(long timeMillis, boolean blocked) {
    }

    private final Supplier<EnforcementSettings> settings;
    private final Map<UUID, Deque<Seen>> players;

    public EnforcementPolicy(Supplier<EnforcementSettings> settings, int maxPlayers) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.players = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Deque<Seen>> eldest) {
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
        boolean kick = current.mode() == EnforcementSettings.Mode.KICK;
        Deque<Seen> seen = players.computeIfAbsent(id, key -> new ArrayDeque<>());
        seen.addLast(new Seen(finding.timeMillis(), finding.mitigated()));
        long oldest = finding.timeMillis() - current.windowSeconds() * 1000L;
        // The size limit keeps one flooding player's history bounded.
        while (!seen.isEmpty()
                && (seen.peekFirst().timeMillis() < oldest || seen.size() > current.findingsToKick() * 2)) {
            seen.removeFirst();
        }
        int count = (int) seen.stream().filter(s -> s.blocked() || !kick).count();
        if (count < current.findingsToKick()) {
            return Optional.empty();
        }
        players.remove(id);
        return Optional.of(new Decision(finding.subject(), finding.checkId(), count, kick));
    }
}
