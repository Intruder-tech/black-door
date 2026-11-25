package io.github.intruder_tech.blackdoor.core.command;

/**
 * Converts {@code &a}-style colour codes into the legacy section-sign format all platforms accept.
 * {@code &&} produces a literal {@code &}; use {@link #escape(String)} for untrusted text.
 */
public final class Text {

    private static final char SECTION = (char) 0x00A7;
    private static final String CODES = "0123456789abcdefklmnor";

    public static final String PREFIX = "&8[&cBlack Door&8] &7";

    private Text() {
    }

    public static String toLegacy(String message) {
        StringBuilder out = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '&' && i + 1 < message.length()) {
                char next = Character.toLowerCase(message.charAt(i + 1));
                if (next == '&') {
                    out.append('&');
                    i++;
                    continue;
                }
                if (CODES.indexOf(next) >= 0) {
                    out.append(SECTION).append(next);
                    i++;
                    continue;
                }
            }
            out.append(c == SECTION ? '?' : c);
        }
        return out.toString();
    }

    /** Visual markers used in chat output (all render on 1.8 through current clients). */
    public static final String BULLET = String.valueOf((char) 0x2022);
    public static final String ARROW = String.valueOf((char) 0x00BB);
    public static final String CHECK = String.valueOf((char) 0x2714);
    public static final String CROSS = String.valueOf((char) 0x2716);

    /** A framed title line with strikethrough rules on both sides. */
    public static String header(String title) {
        return "&8&m          &r &cBlack Door &8" + ARROW + " &f" + title + " &8&m          ";
    }

    /** Closing rule matching {@link #header(String)}. */
    public static String footer() {
        return "&8&m                                                ";
    }

    /** One labelled row: grey label, then the value (value may contain its own colours). */
    public static String row(String label, String value) {
        return " &8" + ARROW + " &7" + label + ": &f" + value;
    }

    /** Escapes untrusted text so it cannot inject colour codes. */
    public static String escape(String untrusted) {
        return untrusted.replace("&", "&&");
    }
}
