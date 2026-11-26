package io.github.intruder_tech.blackdoor.physics.replay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Just enough JSON to read the trace files this project writes; avoids a dependency. */
public final class Json {

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    /** A Map, List, String, Double, Boolean or null. */
    public static Object parse(String text) {
        Json json = new Json(text);
        Object value = json.value(0);
        if (json.at != text.length()) {
            throw new IllegalArgumentException("trailing characters");
        }
        return value;
    }

    private Object value(int depth) {
        if (depth > 16) {
            throw new IllegalArgumentException("too deeply nested");
        }
        char c = peek();
        if (c == '{') {
            Map<String, Object> map = new LinkedHashMap<>();
            at++;
            while (peek() != '}') {
                String key = string();
                expect(':');
                map.put(key, value(depth + 1));
                if (peek() != '}') {
                    expect(',');
                }
            }
            at++;
            return map;
        }
        if (c == '[') {
            List<Object> list = new ArrayList<>();
            at++;
            while (peek() != ']') {
                list.add(value(depth + 1));
                if (peek() != ']') {
                    expect(',');
                }
            }
            at++;
            return list;
        }
        if (c == '"') {
            return string();
        }
        int start = at;
        while (at < text.length() && ",]}".indexOf(text.charAt(at)) < 0) {
            at++;
        }
        String word = text.substring(start, at);
        return switch (word) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            case "null" -> null;
            default -> Double.valueOf(word);
        };
    }

    private String string() {
        expect('"');
        int end = text.indexOf('"', at);
        if (end < 0) {
            throw new IllegalArgumentException("unterminated string");
        }
        String value = text.substring(at, end);
        at = end + 1;
        return value;
    }

    private char peek() {
        if (at >= text.length()) {
            throw new IllegalArgumentException("unexpected end");
        }
        return text.charAt(at);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw new IllegalArgumentException("expected " + c);
        }
        at++;
    }
}
