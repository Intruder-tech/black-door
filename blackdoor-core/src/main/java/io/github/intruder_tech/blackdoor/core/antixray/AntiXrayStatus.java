package io.github.intruder_tech.blackdoor.core.antixray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anti-xray per world: what the files say now ("configured") and what the server started with
 * ("running"). A configured world missing from {@code running} is unknown until a restart.
 */
public record AntiXrayStatus(Map<String, AntiXrayStrength> configured, Map<String, AntiXrayStrength> running) {

    public AntiXrayStatus {
        configured = Collections.unmodifiableMap(new LinkedHashMap<>(configured));
        running = Map.copyOf(running);
    }

    /** The one choice the panel shows: Off if any world is off, Strong only if all are. */
    public AntiXrayStrength level() {
        if (configured.containsValue(AntiXrayStrength.OFF)) {
            return AntiXrayStrength.OFF;
        }
        return configured.containsValue(AntiXrayStrength.NORMAL) || configured.isEmpty()
                ? AntiXrayStrength.NORMAL : AntiXrayStrength.STRONG;
    }

    public List<String> configuredOff() {
        return configured.keySet().stream().filter(w -> configured.get(w) == AntiXrayStrength.OFF).toList();
    }

    /** Worlds the files switch off although the server is running them with anti-xray. */
    public List<String> switchedOff() {
        return configuredOff().stream()
                .filter(w -> running.containsKey(w) && running.get(w) != AntiXrayStrength.OFF).toList();
    }

    /** What is running, for the configured worlds where that is known. */
    public Map<String, AntiXrayStrength> runningKnown() {
        Map<String, AntiXrayStrength> known = new LinkedHashMap<>();
        configured.keySet().stream().filter(running::containsKey).forEach(w -> known.put(w, running.get(w)));
        return known;
    }

    public List<String> runningUnknown() {
        return configured.keySet().stream().filter(w -> !running.containsKey(w)).toList();
    }

    /** The files are not confirmed to be what is running: on, off or strength differs. */
    public boolean pending() {
        return configured.keySet().stream().anyMatch(w -> configured.get(w) != running.get(w));
    }

    /** "Strong" if every world agrees, otherwise "Off in: a; Normal in: b". */
    public static String describe(Map<String, AntiXrayStrength> worlds) {
        List<String> parts = new ArrayList<>();
        for (AntiXrayStrength strength : AntiXrayStrength.values()) {
            List<String> names = worlds.keySet().stream().filter(w -> worlds.get(w) == strength).toList();
            if (names.size() == worlds.size() && !names.isEmpty()) {
                return strength.label();
            }
            if (!names.isEmpty()) {
                parts.add(strength.label() + " in: " + String.join(", ", names));
            }
        }
        return String.join("; ", parts);
    }
}
