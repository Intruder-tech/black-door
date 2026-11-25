package io.github.intruder_tech.blackdoor.common.text;

/**
 * Makes untrusted strings (player names, chat, payload text, reasons) safe to place in logs and
 * audit files (SR-110): control characters, line/paragraph separators, Unicode format characters
 * (for example bidi overrides) and the Minecraft formatting prefix are replaced, and length is capped.
 */
public final class LogSanitizer {

    /** Default maximum length of a sanitized value. */
    public static final int DEFAULT_MAX_LENGTH = 256;

    private static final char REPLACEMENT = '?';
    private static final char SECTION_SIGN = (char) 0x00A7;
    private static final char LINE_SEPARATOR = (char) 0x2028;
    private static final char PARAGRAPH_SEPARATOR = (char) 0x2029;

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        return sanitize(value, DEFAULT_MAX_LENGTH);
    }

    public static String sanitize(String value, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be >= 1");
        }
        if (value == null) {
            return "null";
        }
        int limit = Math.min(value.length(), maxLength);
        StringBuilder out = new StringBuilder(limit + 3);
        for (int i = 0; i < limit; i++) {
            char c = value.charAt(i);
            out.append(isUnsafe(c) ? REPLACEMENT : c);
        }
        if (value.length() > maxLength) {
            out.append("...");
        }
        return out.toString();
    }

    private static boolean isUnsafe(char c) {
        return Character.isISOControl(c)
                || c == SECTION_SIGN
                || c == LINE_SEPARATOR
                || c == PARAGRAPH_SEPARATOR
                || Character.getType(c) == Character.FORMAT;
    }
}
