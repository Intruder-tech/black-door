package io.github.intruder_tech.blackdoor.physics.replay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A recorded golden trace, grouped by client tick. */
public record Trace(Map<String, Object> meta, List<Trace.Tick> ticks) {

    /** The other events of one client tick, in order, and its movement if the client sent one. */
    public record Tick(List<Map<String, Object>> events, Map<String, Object> move) {
    }

    public static Trace read(Reader in) throws IOException {
        BufferedReader lines = new BufferedReader(in);
        Map<String, Object> meta = null;
        List<Tick> ticks = new ArrayList<>();
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> move = null;
        String line;
        for (int number = 1; (line = lines.readLine()) != null; number++) {
            Map<String, Object> event = event(line, number);
            if (meta == null) {
                meta = event;
            } else if ("tick".equals(event.get("k"))) {
                ticks.add(new Tick(events, move));
                events = new ArrayList<>();
                move = null;
            } else if ("move".equals(event.get("k"))) {
                move = event;
            } else {
                events.add(event);
            }
        }
        return new Trace(meta == null ? Map.of() : meta, ticks);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> event(String line, int number) {
        try {
            if (Json.parse(line) instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            throw new IllegalArgumentException("not an object");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("trace line " + number + ": " + e.getMessage(), e);
        }
    }
}
