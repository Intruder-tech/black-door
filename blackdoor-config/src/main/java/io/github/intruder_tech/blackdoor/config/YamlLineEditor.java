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
        // every line keeps its own ending: one file can mix CRLF and LF
        String[] lines = yaml.split("\n", -1);
        String[] ends = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            boolean last = i == lines.length - 1;
            boolean windows = !last && lines[i].endsWith("\r");
            if (windows) {
                lines[i] = lines[i].substring(0, lines[i].length() - 1);
            }
            ends[i] = last ? "" : windows ? "\r\n" : "\n";
        }
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
            return join(List.of(lines), List.of(ends));
        }
        if (appendIfTopLevelMissing) {
            return insertMissing(lines, ends, target, value);
        }
        throw new IllegalArgumentException(dottedPath + " not found in file; add it manually or restore the default file");
    }

    /**
     * Inserts the missing part of {@code target} at the end of the deepest existing ancestor
     * section (or at the end of the document when even the top-level key is absent). Used when a
     * newer version adds a key that older files do not have yet.
     */
    private static String insertMissing(String[] lines, String[] ends, String[] target, String value) {
        int anchorLine = -1;
        int anchorIndent = -1;
        int anchorDepth = 0;
        List<Integer> stackIndent = new ArrayList<>();
        List<String> stackKeys = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher m = KEY_LINE.matcher(lines[i]);
            if (!m.matches()) {
                continue;
            }
            int indent = m.group(1).length();
            while (!stackIndent.isEmpty() && stackIndent.get(stackIndent.size() - 1) >= indent) {
                stackIndent.remove(stackIndent.size() - 1);
                stackKeys.remove(stackKeys.size() - 1);
            }
            stackIndent.add(indent);
            stackKeys.add(m.group(2));
            if (stackKeys.size() < target.length && isPrefix(stackKeys, target) && stackKeys.size() > anchorDepth) {
                String rest = m.group(3);
                int commentAt = commentStart(rest);
                String valuePart = commentAt < 0 ? rest : rest.substring(0, commentAt);
                if (!valuePart.isBlank()) {
                    throw new IllegalArgumentException(String.join(".", stackKeys) + " is a value, not a section");
                }
                anchorLine = i;
                anchorIndent = indent;
                anchorDepth = stackKeys.size();
            }
        }
        List<String> out = new ArrayList<>(List.of(lines));
        List<String> outEnds = new ArrayList<>(List.of(ends));
        int insertAt;
        int baseIndent;
        if (anchorLine < 0) {
            while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
                out.remove(out.size() - 1);
                outEnds.remove(outEnds.size() - 1);
            }
            insertAt = out.size();
            baseIndent = 0;
        } else {
            insertAt = lines.length;
            for (int i = anchorLine + 1; i < lines.length; i++) {
                Matcher m = KEY_LINE.matcher(lines[i]);
                if (m.matches() && m.group(1).length() <= anchorIndent) {
                    insertAt = i;
                    break;
                }
            }
            while (insertAt > anchorLine + 1 && lines[insertAt - 1].isBlank()) {
                insertAt--;
            }
            baseIndent = anchorIndent + 2;
        }
        List<String> added = new ArrayList<>();
        for (int depth = anchorDepth; depth < target.length; depth++) {
            String indent = " ".repeat(baseIndent + 2 * (depth - anchorDepth));
            added.add(indent + target[depth] + ":" + (depth == target.length - 1 ? " " + value : ""));
        }
        // new lines end like the line above them
        String ending = endingBefore(outEnds, insertAt);
        List<String> addedEnds = new ArrayList<>(java.util.Collections.nCopies(added.size(), ending));
        if (insertAt > 0 && outEnds.get(insertAt - 1).isEmpty()) {
            outEnds.set(insertAt - 1, ending);
            if (anchorLine >= 0) {
                addedEnds.set(added.size() - 1, "");
            }
        }
        out.addAll(insertAt, added);
        outEnds.addAll(insertAt, addedEnds);
        return join(out, outEnds);
    }

    /** The nearest line ending above {@code index}; LF when there is none. */
    private static String endingBefore(List<String> ends, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (!ends.get(i).isEmpty()) {
                return ends.get(i);
            }
        }
        return "\n";
    }

    private static String join(List<String> lines, List<String> ends) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            out.append(lines.get(i)).append(ends.get(i));
        }
        return out.toString();
    }

    private static boolean isPrefix(List<String> keys, String[] target) {
        for (int i = 0; i < keys.size(); i++) {
            if (!keys.get(i).equals(target[i])) {
                return false;
            }
        }
        return true;
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
