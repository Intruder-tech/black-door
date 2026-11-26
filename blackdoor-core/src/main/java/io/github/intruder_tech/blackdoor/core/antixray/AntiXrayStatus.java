package io.github.intruder_tech.blackdoor.core.antixray;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anti-xray per world: what the files say now ("configured") and what the server started with
 * ("running"). A configured world missing from {@code running} is unknown until a restart.
 */
public record AntiXrayStatus(Map<String, Boolean> configured, Map<String, Boolean> running) {

    public AntiXrayStatus {
        configured = Collections.unmodifiableMap(new LinkedHashMap<>(configured));
        running = Map.copyOf(running);
    }

    public List<String> configuredOff() {
        return configured.keySet().stream().filter(w -> !configured.get(w)).toList();
    }

    public List<String> runningOff() {
        return configured.keySet().stream().filter(w -> Boolean.FALSE.equals(running.get(w))).toList();
    }

    public List<String> runningUnknown() {
        return configured.keySet().stream().filter(w -> !running.containsKey(w)).toList();
    }

    /** A world is known to run something else than its files now say. */
    public boolean changedSinceStart() {
        return configured.keySet().stream()
                .anyMatch(w -> running.containsKey(w) && !running.get(w).equals(configured.get(w)));
    }

    /** The files are not confirmed to be what is running. */
    public boolean pending() {
        return changedSinceStart() || !runningUnknown().isEmpty();
    }
}
