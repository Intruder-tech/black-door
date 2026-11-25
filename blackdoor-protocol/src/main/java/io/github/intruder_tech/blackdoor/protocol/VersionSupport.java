package io.github.intruder_tech.blackdoor.protocol;

/**
 * Classifies versions against what this build knows about. Versions newer than
 * {@link #LATEST_KNOWN} are treated as unsupported for prediction-based logic until onboarded
 * (docs/PROTOCOL_ARCHITECTURE.md section 6); protocol-level guards still apply.
 */
public final class VersionSupport {

    /** Oldest client/server version in scope. */
    public static final MinecraftVersion MINIMUM = MinecraftVersion.of(1, 8);

    /**
     * Newest version known to this build's version table. Matches the newest release in the pinned
     * PacketEvents version (2.14.0). Raising it is part of version onboarding.
     */
    public static final MinecraftVersion LATEST_KNOWN = MinecraftVersion.of(26, 3);

    public enum Status { TOO_OLD, KNOWN, NEWER_THAN_KNOWN }

    private VersionSupport() {
    }

    public static Status classify(MinecraftVersion version) {
        if (version.isBefore(MINIMUM)) {
            return Status.TOO_OLD;
        }
        if (version.compareTo(LATEST_KNOWN) > 0 && !sameRelease(version, LATEST_KNOWN)) {
            return Status.NEWER_THAN_KNOWN;
        }
        return Status.KNOWN;
    }

    /** Patch releases of the latest known drop (e.g. 26.3.1) are treated as known. */
    private static boolean sameRelease(MinecraftVersion a, MinecraftVersion b) {
        return a.major() == b.major() && a.minor() == b.minor();
    }
}
