package io.github.intruder_tech.blackdoor.core.antixray;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * What the anti-xray files said when this server process started, which is what the server runs.
 * Kept in a file tagged with the process start time, so that re-enabling the plugin without a
 * server restart does not mistake newer files for the running state.
 */
public final class StartupSnapshot {

    private static final String PROCESS_START = "process-start";
    private static final String WORLD = "world.";

    private StartupSnapshot() {
    }

    /**
     * @param processStart start time of this server process
     * @param current      what the files say now; null if they cannot be read
     * @return on or off per world as of this process's start; worlds not in it are unknown
     */
    public static Map<String, Boolean> remember(Path file, long processStart, Map<String, Boolean> current,
                                                Consumer<String> warn) {
        String start = Long.toString(processStart);
        Map<String, Boolean> snapshot = current == null ? Map.of() : current;
        if (Files.exists(file)) {
            Properties saved = new Properties();
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                saved.load(in);
            } catch (IOException | IllegalArgumentException e) {
                saved.clear();
            }
            String savedStart = saved.getProperty(PROCESS_START);
            if (start.equals(savedStart)) {
                return worlds(saved);
            }
            if (savedStart == null) {
                // cannot tell which process wrote it, so claim nothing until the next restart
                warn.accept("The anti-xray startup record was damaged; the running state is unknown until a restart.");
                snapshot = Map.of();
            }
        }
        save(file, start, snapshot, warn);
        return snapshot;
    }

    private static Map<String, Boolean> worlds(Properties saved) {
        Map<String, Boolean> worlds = new LinkedHashMap<>();
        for (String key : saved.stringPropertyNames()) {
            if (key.startsWith(WORLD)) {
                worlds.put(key.substring(WORLD.length()), "on".equals(saved.getProperty(key)));
            }
        }
        return worlds;
    }

    private static void save(Path file, String start, Map<String, Boolean> worlds, Consumer<String> warn) {
        Properties out = new Properties();
        out.setProperty(PROCESS_START, start);
        worlds.forEach((world, on) -> out.setProperty(WORLD + world, on ? "on" : "off"));
        try {
            StringWriter text = new StringWriter();
            out.store(text, "Anti-xray as the server started it. Written by Black Door; do not edit.");
            Files.createDirectories(file.toAbsolutePath().getParent());
            AntiXrayFiles.writeAtomically(file, text.toString());
        } catch (IOException e) {
            warn.accept("Could not save the anti-xray startup record: " + e.getClass().getSimpleName());
        }
    }
}
