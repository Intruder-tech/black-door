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
 * Reads and sets {@code enabled} and {@code engine-mode} under {@code anticheat.anti-xray} in
 * Paper's own configuration files: the world defaults, and in a world's {@code paper-world.yml}
 * each key only where it already has it. Nothing else in a file changes. Paper reads these files at startup only. Blocking file I/O.
 */
public final class AntiXrayFiles {

    /** Appended to a file name for the copy made before Black Door first changes that file. */
    public static final String BACKUP_SUFFIX = ".blackdoor-backup";

    private static final String TEMP_SUFFIX = ".blackdoor-tmp";
    private static final String SECTION = "anticheat.anti-xray.";
    private static final String ENABLED = "enabled";
    private static final String ENGINE_MODE = "engine-mode";
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

    /** What the files say about each world; null when the files cannot tell. */
    public Map<String, AntiXrayStrength> worldStates() {
        try {
            if (!Files.isRegularFile(defaults)) {
                return null;
            }
            Map<String, Object> byDefault = settings(DEFAULTS, read(defaults, DEFAULTS));
            Map<String, AntiXrayStrength> states = new LinkedHashMap<>();
            for (Map.Entry<String, Path> world : worldFolders.entrySet()) {
                Path file = world.getValue().resolve(WORLD_FILE);
                Map<String, Object> effective = new LinkedHashMap<>(byDefault);
                if (Files.isRegularFile(file)) {
                    String name = world.getKey() + "/" + WORLD_FILE;
                    Map<String, Object> own = settings(name, read(file, name));
                    if (own.get(ENABLED) instanceof Boolean enabled) {
                        effective.put(ENABLED, enabled);
                    }
                    if (own.get(ENGINE_MODE) instanceof Number mode) {
                        effective.put(ENGINE_MODE, mode);
                    }
                }
                states.put(world.getKey(), strength(effective));
            }
            return states;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static AntiXrayStrength strength(Map<String, Object> settings) {
        if (!Boolean.TRUE.equals(settings.get(ENABLED))) {
            return AntiXrayStrength.OFF;
        }
        // Paper's own default is engine-mode 1
        return isStrong(settings.get(ENGINE_MODE)) ? AntiXrayStrength.STRONG : AntiXrayStrength.NORMAL;
    }

    private static boolean isStrong(Object engineMode) {
        return engineMode instanceof Number mode && (mode.intValue() == 2 || mode.intValue() == 3);
    }

    /**
     * Sets the strength in the world defaults and in every world file that has its own values.
     * Off leaves engine-mode alone; Strong keeps an existing engine-mode 3. All edits are
     * prepared and checked before the first write; if a write fails, the files already written
     * are put back.
     *
     * @return the files that changed (empty if they already said so)
     * @throws IOException with a message fit to show an operator
     */
    public List<Path> set(AntiXrayStrength strength) throws IOException {
        synchronized (WRITE_LOCK) {
            List<Edit> edits = plan(strength);
            for (Edit edit : edits) {
                backUp(edit);
            }
            write(edits);
            return edits.stream().map(Edit::file).toList();
        }
    }

    private List<Edit> plan(AntiXrayStrength strength) throws IOException {
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
            Map<String, Object> current = settings(name, before);
            if (file.equals(defaults)) {
                require(current, ENABLED);
                if (strength != AntiXrayStrength.OFF) {
                    require(current, ENGINE_MODE);
                }
            }
            Map<String, Object> wanted = changes(current, strength);
            if (!wanted.isEmpty()) {
                edits.add(new Edit(file, name, before, edit(name, before, wanted)));
            }
        }
        return edits;
    }

    private static void require(Map<String, Object> defaults, String key) throws IOException {
        if (defaults.get(key) == null) {
            throw new IOException(DEFAULTS + " has no " + SECTION + key + " setting");
        }
    }

    /** The values to write: only keys the file already has, and only where they differ. */
    private static Map<String, Object> changes(Map<String, Object> current, AntiXrayStrength strength) {
        Map<String, Object> wanted = new LinkedHashMap<>();
        Boolean enabled = strength != AntiXrayStrength.OFF;
        if (current.get(ENABLED) != null && !enabled.equals(current.get(ENABLED))) {
            wanted.put(ENABLED, enabled);
        }
        Object mode = current.get(ENGINE_MODE);
        if (mode != null) {
            if (strength == AntiXrayStrength.NORMAL && !Integer.valueOf(1).equals(mode)) {
                wanted.put(ENGINE_MODE, 1);
            } else if (strength == AntiXrayStrength.STRONG && !isStrong(mode)) {
                wanted.put(ENGINE_MODE, 2);
            }
        }
        return wanted;
    }

    /** Changes the lines and proves that nothing but those values is different afterwards. */
    private static String edit(String name, String before, Map<String, Object> wanted) throws IOException {
        String unsafe = name + " is written in a way Black Door cannot safely edit";
        String after = before;
        try {
            for (Map.Entry<String, Object> value : wanted.entrySet()) {
                after = YamlLineEditor.setScalar(after, SECTION + value.getKey(), value.getValue().toString());
            }
        } catch (IllegalArgumentException e) {
            throw new IOException(unsafe, e);
        }
        Map<String, Object> expected = parse(name, before);
        Map<String, Object> section = section(expected);
        if (section == null) {
            throw new IOException(unsafe);
        }
        section.putAll(wanted);
        if (!expected.equals(parse(name, after))) {
            throw new IOException(unsafe);
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

    /** The values under {@code anticheat.anti-xray}; empty if the file has none. */
    private static Map<String, Object> settings(String name, String content) throws IOException {
        Map<String, Object> section = section(parse(name, content));
        return section == null ? Map.of() : section;
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
