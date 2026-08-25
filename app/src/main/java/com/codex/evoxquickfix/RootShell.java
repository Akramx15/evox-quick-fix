package com.codex.evoxquickfix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class RootShell {
    private static final long DEFAULT_TIMEOUT_SECONDS = 20L;

    private RootShell() {}

    static CommandResult run(String fixedCommand) {
        return run(fixedCommand, DEFAULT_TIMEOUT_SECONDS);
    }

    static CommandResult run(String fixedCommand, long timeoutSeconds) {
        return execute(new String[]{"su", "-c", fixedCommand}, timeoutSeconds);
    }

    static CommandResult runUnprivileged(String... argv) {
        return execute(argv, DEFAULT_TIMEOUT_SECONDS);
    }

    private static CommandResult execute(String[] argv, long timeoutSeconds) {
        Process process = null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            process = new ProcessBuilder(argv).redirectErrorStream(true).start();
            Process captured = process;
            Thread reader = new Thread(() -> copy(captured.getInputStream(), output), "root-output");
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1_000L);
                return new CommandResult(-1, output.toString(StandardCharsets.UTF_8), true);
            }
            reader.join(1_000L);
            return new CommandResult(process.exitValue(), output.toString(StandardCharsets.UTF_8), false);
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new CommandResult(-1, e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        }
    }

    private static void copy(InputStream input, ByteArrayOutputStream output) {
        byte[] buffer = new byte[4_096];
        int read;
        try (input) {
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                if (output.size() > 512_000) {
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }
}
