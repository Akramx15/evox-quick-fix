package com.codex.evoxquickfix;

final class ShellEscaper {
    private ShellEscaper() {}

    static String quote(String value) {
        if (value == null) {
            throw new IllegalArgumentException("null shell value");
        }
        if (value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("unsafe shell value");
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
