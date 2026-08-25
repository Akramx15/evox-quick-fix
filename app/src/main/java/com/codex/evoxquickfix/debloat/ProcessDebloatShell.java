package com.codex.evoxquickfix.debloat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Production root executor. Kept separate because the application's RootShell is package-private. */
public final class ProcessDebloatShell implements DebloatShell {
    private static final long DEFAULT_TIMEOUT_SECONDS = 20L;

    private final long timeoutSeconds;

    public ProcessDebloatShell() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    public ProcessDebloatShell(long timeoutSeconds) {
        if (timeoutSeconds <= 0L) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ShellResult run(String fixedCommand) {
        if (fixedCommand == null || fixedCommand.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid command");
        }
        Process process = null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            process = new ProcessBuilder("su", "-c", fixedCommand)
                    .redirectErrorStream(true)
                    .start();
            Process captured = process;
            Thread reader = new Thread(() -> copy(captured.getInputStream(), output),
                    "debloat-root-output");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reader.join(1_000L);
                return new ShellResult(-1, output.toString(StandardCharsets.UTF_8), true);
            }
            reader.join(1_000L);
            return new ShellResult(process.exitValue(),
                    output.toString(StandardCharsets.UTF_8), false);
        } catch (Exception failure) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new ShellResult(-1, failure.getClass().getSimpleName() + ": "
                    + failure.getMessage(), false);
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
