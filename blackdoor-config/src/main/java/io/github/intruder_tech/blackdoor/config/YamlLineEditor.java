package io.github.intruder_tech.blackdoor.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changes one scalar value in a block-style YAML document while keeping every comment, blank
 * line and the original key order. Used for in-game edits so that operators' hand-written
 * comments survive. Only existing keys can be changed; the caller validates the result.
 */
public final class YamlLineEditor {

    private static final Pattern KEY_LINE = Pattern.compile("^( *)([A-Za-z0-9_-]+):(.*)$");
    private static final Pattern SAFE_SCALAR = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private YamlLineEditor() {
    }

    /**
     * @param value plain scalar (letters, digits, {@code _ . -}); quoting is not supported on purpose
     * @throws IllegalArgumentException if the key is missing, is a section, or the value is unsafe
     */
    public static String setScalar(String yaml, String dottedPath, String value) {
        return setScalar(yaml, dottedPath, value, false);
    }

    /** As {@link #setScalar(String, String, String)}; optionally appends the whole path if its top-level key is absent. */
    public static String setScalar(String yaml, String dottedPath, String value, boolean appendIfTopLevelMissing) {
        if (!SAFE_SCALAR.matcher(value).matches()) {
            throw new IllegalArgumentException("unsupported value");
        }
        String newline = yaml.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = yaml.split("\r?\n", -1);
        String[] target = dottedPath.split("[.]");
        List<int[]> stackIndent = new ArrayList<>();
        List<String> stackKeys = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher m = KEY_LINE.matcher(lines[i]);
            if (!m.matches()) {
                continue;
            }
            int indent = m.group(1).length();
            while (!stackIndent.isEmpty() && stackIndent.get(stackIndent.size() - 1)[0] >= indent) {
                stackIndent.remove(stackIndent.size() - 1);
                stackKeys.remove(stackKeys.size() - 1);
            }
            stackIndent.add(new int[]{indent});
            stackKeys.add(m.group(2));
            if (!matches(stackKeys, target)) {
                continue;
            }
            String rest = m.group(3);
            int commentAt = commentStart(rest);
            String valuePart = commentAt < 0 ? rest : rest.substring(0, commentAt);
            if (valuePart.isBlank()) {
                throw new IllegalArgumentException(dottedPath + " is a section, not a value");
            }
            String comment = commentAt < 0 ? "" : "  " + rest.substring(commentAt).trim();
            lines[i] = m.group(1) + m.group(2) + ": " + value + comment;
            return String.join(newline, lines);
        }
        if (appendIfTopLevelMissing && !hasTopLevelKey(lines, target[0])) {
            StringBuilder out = new StringBuilder(String.join(newline, lines));
            if (out.length() > 0 && !out.toString().endsWith(newline)) {
                out.append(newline);
            }
            for (int depth = 0; depth < target.length; depth++) {
                out.append("  ".repeat(depth)).append(target[depth]).append(":");
                out.append(depth == target.length - 1 ? " " + value : "").append(newline);
            }
            return out.toString();
        }
        throw new IllegalArgumentException(dottedPath + " not found in file; add it manually or restore the default file");
    }

    private static boolean hasTopLevelKey(String[] lines, String key) {
        for (String line : lines) {
            if (line.startsWith(key + ":")) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(List<String> keys, String[] target) {
        if (keys.size() != target.length) {
            return false;
        }
        for (int i = 0; i < target.length; i++) {
            if (!keys.get(i).equals(target[i])) {
                return false;
            }
        }
        return true;
    }

    /** Index of a '#' that starts a comment (preceded by whitespace and outside quotes), or -1. */
    private static int commentStart(String rest) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '\'' && !dbl) {
                single = !single;
            } else if (c == '"' && !single) {
                dbl = !dbl;
            } else if (c == '#' && !single && !dbl && (i == 0 || Character.isWhitespace(rest.charAt(i - 1)))) {
                return i;
            }
        }
        return -1;
    }
}
