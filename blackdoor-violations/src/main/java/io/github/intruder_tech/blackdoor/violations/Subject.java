package io.github.intruder_tech.blackdoor.violations;

import java.util.UUID;

/** Who a finding is about. Either field may be null before login completes. */
public record Subject(UUID uniqueId, String name) {

    public static final Subject UNKNOWN = new Subject(null, null);

    public String displayName() {
        return name != null ? name : uniqueId != null ? uniqueId.toString() : "unknown";
    }
}
