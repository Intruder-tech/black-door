package io.github.intruder_tech.blackdoor.config;

import io.github.intruder_tech.blackdoor.common.text.LogSanitizer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads YAML with hard limits (SR-120): file size, alias count (billion-laughs), nesting depth,
 * duplicate keys, and only safe standard types (no arbitrary class construction).
 */
public final class YamlLoader {

    /** Maximum size of a configuration file. */
    public static final long MAX_FILE_BYTES = 1024 * 1024;
    static final int MAX_ALIASES = 20;
    static final int MAX_NESTING_DEPTH = 32;

    private YamlLoader() {
    }

    /** Loads a YAML mapping from disk. An empty file yields an empty map. */
    public static Map<String, Object> load(Path file) throws ConfigLoadException {
        String name = file.getFileName().toString();
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                throw new ConfigLoadException(ConfigError.error(name, "",
                        "file is " + size + " bytes; maximum is " + MAX_FILE_BYTES));
            }
            return parse(name, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ConfigLoadException(ConfigError.error(name, "", "cannot read file: " + e.getClass().getSimpleName()));
        }
    }

    /** Parses YAML text that must be a mapping at the root. */
    public static Map<String, Object> parse(String name, String content) throws ConfigLoadException {
        if (content.length() > MAX_FILE_BYTES) {
            throw new ConfigLoadException(ConfigError.error(name, "", "content too large"));
        }
        Object root;
        try {
            root = newLoader().load(content);
        } catch (YAMLException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new ConfigLoadException(ConfigError.error(name, "", "invalid YAML: " + LogSanitizer.sanitize(reason, 300)));
        } catch (StackOverflowError e) {
            throw new ConfigLoadException(ConfigError.error(name, "", "invalid YAML: structure too deep"));
        }
        if (root == null) {
            return new LinkedHashMap<>();
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new ConfigLoadException(ConfigError.error(name, "", "root must be a mapping of keys to values"));
        }
        return normalize(map);
    }

    /** Serializes a mapping back to block-style YAML (used by migrations). */
    public static String dump(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(root);
    }

    private static Yaml newLoader() {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(MAX_ALIASES);
        options.setNestingDepthLimit(MAX_NESTING_DEPTH);
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setCodePointLimit((int) MAX_FILE_BYTES);
        return new Yaml(new SafeConstructor(options));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalize(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object value = e.getValue();
            if (value instanceof Map<?, ?> nested) {
                value = normalize(nested);
            }
            out.put(String.valueOf(e.getKey()), value);
        }
        return out;
    }
}
