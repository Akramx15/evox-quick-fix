package com.codex.evoxquickfix.debloat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict, dependency-free JSON codec so local JVM tests do not depend on android.jar JSON stubs. */
final class DebloatLedgerCodec {
    private static final Set<String> TOP_KEYS = Set.of(
            "schema", "revision", "profileHash", "phase", "nextSequence",
            "activeOrder", "packages");
    private static final Set<String> ENTRY_KEYS = Set.of(
            "id", "baselineState", "managed", "pending", "sequence");

    String encode(DebloatLedger ledger) {
        ledger.validate();
        StringBuilder out = new StringBuilder(512);
        out.append("{\n")
                .append("  \"schema\": ").append(ledger.schema).append(",\n")
                .append("  \"revision\": ").append(ledger.revision).append(",\n")
                .append("  \"profileHash\": ");
        string(out, ledger.profileHash);
        out.append(",\n  \"phase\": ");
        string(out, ledger.phase.name());
        out.append(",\n  \"nextSequence\": ").append(ledger.nextSequence)
                .append(",\n  \"activeOrder\": [");
        for (int index = 0; index < ledger.activeOrder.size(); index++) {
            if (index > 0) {
                out.append(", ");
            }
            string(out, ledger.activeOrder.get(index));
        }
        out.append("],\n  \"packages\": [");
        int index = 0;
        for (DebloatLedger.Entry entry : ledger.entries.values()) {
            if (index++ > 0) {
                out.append(',');
            }
            out.append("\n    {\"id\": ");
            string(out, entry.packageName);
            out.append(", \"baselineState\": ").append(entry.baselineState.value)
                    .append(", \"managed\": ").append(entry.managed)
                    .append(", \"pending\": ").append(entry.pending)
                    .append(", \"sequence\": ").append(entry.sequence).append('}');
        }
        if (!ledger.entries.isEmpty()) {
            out.append('\n').append("  ");
        }
        return out.append("]\n}\n").toString();
    }

    DebloatLedger decode(String json) {
        Object parsed = new Parser(json).parse();
        Map<String, Object> root = object(parsed, "ledger");
        requireOnly(root, TOP_KEYS, "ledger");
        int schema = integer(root, "schema");
        if (schema != DebloatLedger.SCHEMA) {
            throw new IllegalStateException("unsupported debloat ledger schema: " + schema);
        }
        DebloatLedger ledger = new DebloatLedger(text(root, "profileHash"));
        ledger.schema = schema;
        ledger.revision = number(root, "revision");
        ledger.phase = DebloatLedger.Phase.valueOf(text(root, "phase"));
        ledger.nextSequence = number(root, "nextSequence");
        for (Object value : array(root, "activeOrder")) {
            ledger.activeOrder.add(PackageId.requireValid(asString(value, "active package")));
        }
        for (Object value : array(root, "packages")) {
            Map<String, Object> encoded = object(value, "package entry");
            requireOnly(encoded, ENTRY_KEYS, "package entry");
            String packageName = PackageId.requireValid(text(encoded, "id"));
            DebloatLedger.Entry entry = ledger.createEntry(packageName,
                    PackageEnabledState.fromValue(integer(encoded, "baselineState")));
            entry.managed = bool(encoded, "managed");
            entry.pending = bool(encoded, "pending");
            entry.sequence = number(encoded, "sequence");
        }
        ledger.validate();
        return ledger;
    }

    private static void string(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
            }
        }
        out.append('"');
    }

    private static void requireOnly(Map<String, Object> value, Set<String> expected,
                                    String description) {
        if (!value.keySet().equals(expected)) {
            throw new IllegalStateException(description + " keys do not match schema 1");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String description) {
        if (!(value instanceof Map)) {
            throw new IllegalStateException(description + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Map<String, Object> value, String key) {
        Object item = value.get(key);
        if (!(item instanceof List)) {
            throw new IllegalStateException(key + " must be an array");
        }
        return (List<Object>) item;
    }

    private static String text(Map<String, Object> value, String key) {
        return asString(value.get(key), key);
    }

    private static String asString(Object value, String description) {
        if (!(value instanceof String)) {
            throw new IllegalStateException(description + " must be text");
        }
        return (String) value;
    }

    private static long number(Map<String, Object> value, String key) {
        Object item = value.get(key);
        if (!(item instanceof Long)) {
            throw new IllegalStateException(key + " must be an integer");
        }
        return (Long) item;
    }

    private static int integer(Map<String, Object> value, String key) {
        long number = number(value, key);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalStateException(key + " is out of range");
        }
        return (int) number;
    }

    private static boolean bool(Map<String, Object> value, String key) {
        Object item = value.get(key);
        if (!(item instanceof Boolean)) {
            throw new IllegalStateException(key + " must be boolean");
        }
        return (Boolean) item;
    }

    private static final class Parser {
        private final String input;
        private int cursor;

        Parser(String input) {
            if (input == null) {
                throw new IllegalArgumentException("json is null");
            }
            this.input = input;
        }

        Object parse() {
            Object value = value();
            whitespace();
            if (cursor != input.length()) {
                fail("trailing data");
            }
            return value;
        }

        private Object value() {
            whitespace();
            if (cursor >= input.length()) fail("unexpected end");
            char character = input.charAt(cursor);
            if (character == '{') return object();
            if (character == '[') return array();
            if (character == '"') return string();
            if (character == '-' || Character.isDigit(character)) return number();
            if (input.startsWith("true", cursor)) { cursor += 4; return Boolean.TRUE; }
            if (input.startsWith("false", cursor)) { cursor += 5; return Boolean.FALSE; }
            if (input.startsWith("null", cursor)) { cursor += 4; return null; }
            fail("unexpected token");
            return null;
        }

        private Map<String, Object> object() {
            cursor++;
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return result;
            while (true) {
                whitespace();
                if (cursor >= input.length() || input.charAt(cursor) != '"') {
                    fail("object key expected");
                }
                String key = string();
                whitespace();
                require(':');
                if (result.containsKey(key)) {
                    fail("duplicate object key");
                }
                result.put(key, value());
                whitespace();
                if (take('}')) return result;
                require(',');
            }
        }

        private List<Object> array() {
            cursor++;
            List<Object> result = new ArrayList<>();
            whitespace();
            if (take(']')) return result;
            while (true) {
                result.add(value());
                whitespace();
                if (take(']')) return result;
                require(',');
            }
        }

        private String string() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (cursor < input.length()) {
                char character = input.charAt(cursor++);
                if (character == '"') return result.toString();
                if (character == '\\') {
                    if (cursor >= input.length()) fail("bad escape");
                    char escaped = input.charAt(cursor++);
                    switch (escaped) {
                        case '"': result.append('"'); break;
                        case '\\': result.append('\\'); break;
                        case '/': result.append('/'); break;
                        case 'b': result.append('\b'); break;
                        case 'f': result.append('\f'); break;
                        case 'n': result.append('\n'); break;
                        case 'r': result.append('\r'); break;
                        case 't': result.append('\t'); break;
                        case 'u':
                            if (cursor + 4 > input.length()) fail("bad unicode escape");
                            try {
                                result.append((char) Integer.parseInt(
                                        input.substring(cursor, cursor + 4), 16));
                            } catch (NumberFormatException failure) {
                                fail("bad unicode escape");
                            }
                            cursor += 4;
                            break;
                        default: fail("bad escape");
                    }
                } else {
                    if (character < 0x20) fail("control character in string");
                    result.append(character);
                }
            }
            fail("unterminated string");
            return "";
        }

        private Long number() {
            int start = cursor;
            if (input.charAt(cursor) == '-') cursor++;
            if (cursor >= input.length() || !Character.isDigit(input.charAt(cursor))) {
                fail("bad number");
            }
            while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) cursor++;
            try {
                return Long.parseLong(input.substring(start, cursor));
            } catch (NumberFormatException failure) {
                fail("bad number");
                return 0L;
            }
        }

        private void whitespace() {
            while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) cursor++;
        }

        private boolean take(char expected) {
            if (cursor < input.length() && input.charAt(cursor) == expected) {
                cursor++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!take(expected)) fail("expected " + expected);
        }

        private void fail(String message) {
            throw new IllegalStateException("invalid debloat ledger JSON at " + cursor + ": " + message);
        }
    }
}
