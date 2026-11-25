package io.github.intruder_tech.blackdoor.config;

import java.util.List;
import java.util.Set;

/**
 * Outcome of a (re)load. On failure the previously active configuration stays in effect
 * ({@code usingDefaults} tells whether that is only the bundled defaults).
 */
public record ReloadResult(boolean success, boolean usingDefaults, List<ConfigError> errors,
                           List<ConfigError> warnings, Set<String> changedFiles, List<String> notes) {

    public ReloadResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
        changedFiles = Set.copyOf(changedFiles);
        notes = List.copyOf(notes);
    }
}
