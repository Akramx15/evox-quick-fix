package com.codex.evoxquickfix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON reader kept host-JVM-testable without Android framework stubs. */
final class DebloatJsonParser {
    private static final int MAX_INPUT_CHARS = 2_000_000;
    private static final int MAX_DEPTH = 32;

    private final String text;
    private int position;

    private DebloatJsonParser(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("JSON is empty");
        }
        if (text.length() > MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("JSON is too large");
        }
        DebloatJsonParser parser = new DebloatJsonParser(text);
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw parser.error("Trailing JSON content");
        }
        return value;
    }

    private Object readValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("JSON nesting is too deep");
        }
        skipWhitespace();
        if (position >= text.length()) {
            throw error("Unexpected end of JSON");
        }
        return switch (text.charAt(position)) {
            case '{' -> readObject(depth + 1);
            case '[' -> readArray(depth + 1);
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject(int depth) {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (consume('}')) {
            return result;
        }
        while (true) {
            skipWhitespace();
            if (position >= text.length() || text.charAt(position) != '"') {
                throw error("Object key must be a string");
            }
            String key = readString();
            if (result.containsKey(key)) {
                throw error("Duplicate object key: " + key);
            }
            skipWhitespace();
            expect(':');
            result.put(key, readValue(depth));
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            expect(',');
        }
    }

    private List<Object> readArray(int depth) {
        expect('[');
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (consume(']')) {
            return result;
        }
        while (true) {
            result.add(readValue(depth));
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (position < text.length()) {
            char value = text.charAt(position++);
            if (value == '"') {
                return result.toString();
            }
            if (value < 0x20) {
                throw error("Unescaped control character in string");
            }
            if (value != '\\') {
                result.append(value);
                continue;
            }
            if (position >= text.length()) {
                throw error("Unfinished string escape");
            }
            char escaped = text.charAt(position++);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> result.append(readUnicodeEscape());
                default -> throw error("Unsupported string escape");
            }
        }
        throw error("Unterminated string");
    }

    private char readUnicodeEscape() {
        if (position + 4 > text.length()) {
            throw error("Incomplete unicode escape");
        }
        int value = 0;
        for (int index = 0; index < 4; index++) {
            int digit = Character.digit(text.charAt(position++), 16);
            if (digit < 0) {
                throw error("Invalid unicode escape");
            }
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, position)) {
            throw error("Invalid JSON literal");
        }
        position += literal.length();
        return value;
    }

    private Number readNumber() {
        int start = position;
        if (consume('-')) {
            // Sign consumed.
        }
        if (consume('0')) {
            if (position < text.length() && Character.isDigit(text.charAt(position))) {
                throw error("Leading zero in number");
            }
        } else {
            readDigits();
        }
        boolean decimal = false;
        if (consume('.')) {
            decimal = true;
            readDigits();
        }
        if (position < text.length()
                && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
            decimal = true;
            position++;
            if (position < text.length()
                    && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                position++;
            }
            readDigits();
        }
        if (start == position) {
            throw error("Expected JSON value");
        }
        String value = text.substring(start, position);
        try {
            return decimal ? Double.valueOf(value) : Long.valueOf(value);
        } catch (NumberFormatException failure) {
            throw error("Invalid number");
        }
    }

    private void readDigits() {
        int start = position;
        while (position < text.length() && Character.isDigit(text.charAt(position))) {
            position++;
        }
        if (start == position) {
            throw error("Expected number digit");
        }
    }

    private void skipWhitespace() {
        while (position < text.length()) {
            char value = text.charAt(position);
            if (value != ' ' && value != '\n' && value != '\r' && value != '\t') {
                return;
            }
            position++;
        }
    }

    private boolean consume(char expected) {
        if (position < text.length() && text.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!consume(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + position);
    }
}
