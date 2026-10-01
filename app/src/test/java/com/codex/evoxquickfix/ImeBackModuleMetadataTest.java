package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public final class ImeBackModuleMetadataTest {
    private static final Path XPOSED_METADATA = Path.of(
            "src", "main", "resources", "META-INF", "xposed");

    @Test
    public void javaInitRegistersIndependentModuleEntries() throws Exception {
        assertEquals(
                List.of(
                        "com.codex.evoxquickfix.BackGuardModule",
                        "com.codex.evoxquickfix.ImeBackModule"),
                meaningfulLines(XPOSED_METADATA.resolve("java_init.list")));
    }

    @Test
    public void staticScopeAddsOnlyHeliBoardForImeHooks() throws Exception {
        assertEquals(
                List.of("com.tk.quicksearch", ImeBackPolicy.HELIBOARD_PACKAGE),
                meaningfulLines(XPOSED_METADATA.resolve("scope.list")));
    }

    private static List<String> meaningfulLines(Path file) throws Exception {
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }
}
