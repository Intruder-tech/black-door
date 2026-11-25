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

    /** Escapes untrusted text so it cannot inject colour codes. */
    public static String escape(String untrusted) {
        return untrusted.replace("&", "&&");
    }
}
