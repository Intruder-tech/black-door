package io.github.intruder_tech.blackdoor.protocol;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Minecraft Java Edition release version. Handles both the legacy {@code 1.x.y} scheme and the
 * year-based scheme introduced with 26.1 ({@code 26.1}, {@code 26.3.1}). Year-based versions
 * compare greater than every {@code 1.x} version because their major component is larger.
 *
 * <p>Pre-release and snapshot identifiers are not represented; parsing reads the leading release
 * number only (for example {@code 1.20.1-R0.1-SNAPSHOT} parses as {@code 1.20.1}).
 */
public record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {

    private static final Pattern LEADING = Pattern.compile("^([0-9]{1,4})[.]([0-9]{1,4})(?:[.]([0-9]{1,4}))?(?![0-9]|[.][0-9])");
    private static final Comparator<MinecraftVersion> ORDER = Comparator
            .comparingInt(MinecraftVersion::major)
            .thenComparingInt(MinecraftVersion::minor)
            .thenComparingInt(MinecraftVersion::patch);

    public MinecraftVersion {
        if (major < 1 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("invalid version " + major + "." + minor + "." + patch);
        }
    }

    public static MinecraftVersion of(int major, int minor, int patch) {
        return new MinecraftVersion(major, minor, patch);
    }

    public static MinecraftVersion of(int major, int minor) {
        return new MinecraftVersion(major, minor, 0);
    }

    /** Parses the leading release number of a version string. */
    public static Optional<MinecraftVersion> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = LEADING.matcher(text.trim());
        if (!m.find()) {
            return Optional.empty();
        }
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int patch = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        if (major < 1) {
            return Optional.empty();
        }
        return Optional.of(new MinecraftVersion(major, minor, patch));
    }

    /** Like {@link #parse(String)} but throws for unparseable input. */
    public static MinecraftVersion parseOrThrow(String text) {
        return parse(text).orElseThrow(() -> new IllegalArgumentException("not a Minecraft version: " + text));
    }

    /** True for the year-based scheme (26.1 and later). */
    public boolean isYearBased() {
        return major >= 26;
    }

    public boolean isAtLeast(MinecraftVersion other) {
        return compareTo(other) >= 0;
    }

    public boolean isBefore(MinecraftVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
    }
}
