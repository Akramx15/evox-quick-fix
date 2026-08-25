package com.codex.evoxquickfix.debloat;

/** Executes a fixed root command. Implementations must not invoke a second shell layer. */
public interface DebloatShell {
    ShellResult run(String fixedCommand);

    final class ShellResult {
        public final int exitCode;
        public final String output;
        public final boolean timedOut;

        public ShellResult(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.timedOut = timedOut;
        }

        public boolean ok() {
            return !timedOut && exitCode == 0;
        }

        public void requireSuccess(String operation) {
            if (!ok()) {
                throw new IllegalStateException(operation + " failed (exit=" + exitCode
                        + ", timedOut=" + timedOut + "): " + output.trim());
            }
        }
    }
}
