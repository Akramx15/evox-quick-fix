package com.codex.evoxquickfix.debloat;

import java.util.regex.Pattern;

final class PackageId {
    private static final Pattern VALID = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+");

    private PackageId() {}

    static String requireValid(String value) {
        if (value == null || !("android".equals(value) || VALID.matcher(value).matches())) {
            throw new IllegalArgumentException("invalid package id: " + value);
        }
        return value;
    }

    static String shellQuote(String value) {
        requireValid(value);
        return "'" + value + "'";
    }

    static String quotePath(String value) {
        if (value == null || value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("invalid path");
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
