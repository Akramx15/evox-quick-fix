package com.codex.evoxquickfix;

final class CommandResult {
    final int exitCode;
    final String output;
    final boolean timedOut;

    CommandResult(int exitCode, String output, boolean timedOut) {
        this.exitCode = exitCode;
        this.output = output == null ? "" : output.trim();
        this.timedOut = timedOut;
    }

    boolean ok() {
        return !timedOut && exitCode == 0;
    }

    void requireSuccess(String operation) {
        if (!ok()) {
            throw new IllegalStateException(operation + " فشل (exit=" + exitCode + "): " + output);
        }
    }
}
