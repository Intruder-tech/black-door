package io.github.intruder_tech.blackdoor.core.antixray;

import io.github.intruder_tech.blackdoor.config.ConfigLoadException;
import io.github.intruder_tech.blackdoor.config.YamlLineEditor;
import io.github.intruder_tech.blackdoor.config.YamlLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and switches {@code anticheat.anti-xray.enabled} in Paper's own configuration files: the
 * world defaults, and a world's {@code paper-world.yml} only where it already has that key.
 * Nothing else in a file changes. Paper reads these files at startup only. Blocking file I/O.
 */
public final class AntiXrayFiles {

    /** Appended to a file name for the copy made before Black Door first changes that file. */
    public static final String BACKUP_SUFFIX = ".blackdoor-backup";

    private static final String TEMP_SUFFIX = ".blackdoor-tmp";
    private static final String KEY = "anticheat.anti-xray.enabled";
    private static final String DEFAULTS = "config/paper-world-defaults.yml";
    private static final String WORLD_FILE = "paper-world.yml";
    /** One change at a time, even when two operators click at once. */
    private static final Object WRITE_LOCK = new Object();

    private final Path defaults;
    private final Map<String, Path> worldFolders;

    /** A planned edit of one file. */
    private record Edit(Path file, String name, String before, String after) {
    }

    /**
     * @param serverRoot   the server folder, which holds {@code config/}
     * @param worldFolders loaded worlds by name, in the order they should be reported
     */
    public AntiXrayFiles(Path serverRoot, Map<String, Path> worldFolders) {
        this.defaults = serverRoot.resolve("config").resolve("paper-world-defaults.yml");
        this.worldFolders = new LinkedHashMap<>(worldFolders);
    }

    /** Whether the files say anti-xray is on, per world; null when the files cannot tell. */
    public Map<String, Boolean> worldStates() {
        try {
            if (!Files.isRegularFile(defaults)) {
                return null;
            }
            boolean byDefault = Boolean.TRUE.equals(value(parse(DEFAULTS, read(defaults, DEFAULTS))));
            Map<String, Boolean> states = new LinkedHashMap<>();
            for (Map.Entry<String, Path> world : worldFolders.entrySet()) {
                Path file = world.getValue().resolve(WORLD_FILE);
                boolean on = byDefault;
                if (Files.isRegularFile(file)) {
                    String name = world.getKey() + "/" + WORLD_FILE;
                    if (value(parse(name, read(file, name))) instanceof Boolean override) {
                        on = override;
                    }
                }
                states.put(world.getKey(), on);
            }
            return states;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Sets the switch in the world defaults and in every world file that has its own. All edits
     * are prepared and checked before the first write; if a write fails, the files already
     * written are put back.
     *
     * @return the files that changed (empty if they already said so)
     * @throws IOException with a message fit to show an operator
     */
    public List<Path> setEnabled(boolean enabled) throws IOException {
        synchronized (WRITE_LOCK) {
            List<Edit> edits = plan(enabled);
            for (Edit edit : edits) {
                backUp(edit);
            }
            write(edits);
            return edits.stream().map(Edit::file).toList();
        }
    }

    private List<Edit> plan(boolean enabled) throws IOException {
        if (!Files.isRegularFile(defaults)) {
            throw new IOException(DEFAULTS + " was not found");
        }
        Map<Path, String> targets = new LinkedHashMap<>();
        targets.put(defaults, DEFAULTS);
        worldFolders.forEach((world, folder) -> {
            Path file = folder.resolve(WORLD_FILE);
            if (Files.isRegularFile(file)) {
                targets.putIfAbsent(file, world + "/" + WORLD_FILE);
            }
        });
        List<Edit> edits = new ArrayList<>();
        for (Map.Entry<Path, String> target : targets.entrySet()) {
            Path file = target.getKey();
            String name = target.getValue();
            String before = read(file, name);
            Object current = value(parse(name, before));
            if (current == null) {
                if (file.equals(defaults)) {
                    throw new IOException(name + " has no " + KEY + " setting");
                }
                continue;
            }
            if (!Boolean.valueOf(enabled).equals(current)) {
                edits.add(new Edit(file, name, before, edit(name, before, enabled)));
            }
        }
        return edits;
    }

    /** Changes the one line and proves that nothing but the switch is different afterwards. */
    private static String edit(String name, String before, boolean enabled) throws IOException {
        String after;
        try {
            after = YamlLineEditor.setScalar(before, KEY, Boolean.toString(enabled));
        } catch (IllegalArgumentException e) {
            throw new IOException(name + " is written in a way Black Door cannot safely edit", e);
        }
        Map<String, Object> expected = parse(name, before);
        if (!(section(expected) instanceof Map<?, ?>)) {
            throw new IOException(name + " is written in a way Black Door cannot safely edit");
        }
        section(expected).put("enabled", enabled);
        if (!expected.equals(parse(name, after))) {
            throw new IOException(name + " is written in a way Black Door cannot safely edit");
        }
        return after;
    }

    /** The {@code anticheat.anti-xray} mapping, or null. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root) {
        if (root.get("anticheat") instanceof Map<?, ?> anticheat
                && ((Map<String, Object>) anticheat).get("anti-xray") instanceof Map<?, ?> antiXray) {
            return (Map<String, Object>) antiXray;
        }
        return null;
    }

    private static Object value(Map<String, Object> root) {
        Map<String, Object> section = section(root);
        return section == null ? null : section.get("enabled");
    }

    private static Map<String, Object> parse(String name, String content) throws IOException {
        try {
            return YamlLoader.parse(name, content);
        } catch (ConfigLoadException e) {
            throw new IOException(name + " could not be read as YAML", e);
        }
    }

    private static String read(Path file, String name) throws IOException {
        try {
            if (Files.size(file) > YamlLoader.MAX_FILE_BYTES) {
                throw new IOException(name + " is too large");
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (java.nio.file.FileSystemException | java.nio.charset.CharacterCodingException e) {
            throw new IOException(name + " could not be read", e);
        }
    }

    private static void backUp(Edit edit) throws IOException {
        Path backup = edit.file().resolveSibling(edit.file().getFileName() + BACKUP_SUFFIX);
        if (Files.exists(backup)) {
            return;
        }
        try {
            writeAtomically(backup, edit.before());
        } catch (IOException e) {
            throw new IOException("could not back up " + edit.name(), e);
        }
    }

    private static void write(List<Edit> edits) throws IOException {
        for (int i = 0; i < edits.size(); i++) {
            Edit edit = edits.get(i);
            try {
                writeAtomically(edit.file(), edit.after());
            } catch (IOException | RuntimeException e) {
                List<String> stuck = restore(edits.subList(0, i));
                throw new IOException("could not write " + edit.name() + (stuck.isEmpty() ? ""
                        : "; these stayed changed, restore them from their " + BACKUP_SUFFIX + " copy: "
                        + String.join(", ", stuck)), e);
            }
        }
    }

    /** Puts the earlier content back and returns the names of files where that failed too. */
    private static List<String> restore(List<Edit> written) {
        List<String> stuck = new ArrayList<>();
        for (Edit edit : written) {
            try {
                writeAtomically(edit.file(), edit.before());
            } catch (IOException | RuntimeException e) {
                stuck.add(edit.name());
            }
        }
        return stuck;
    }

    /** Writes a temporary file, flushes it to disk, then moves it over the target. */
    static void writeAtomically(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                discard(temp);
            }
        }
    }

    /** Best effort: the failure that brought us here is the one to report. */
    private static void discard(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException | RuntimeException ignored) {
            // nothing more can be done about a temporary file that will not go away
        }
    }
}
