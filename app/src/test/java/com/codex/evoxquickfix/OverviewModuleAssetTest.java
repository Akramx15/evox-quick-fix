package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.Test;

public final class OverviewModuleAssetTest {
    private static final Path ASSETS = Path.of(
            "src", "main", "assets", "overview_module");

    @Test
    public void ownershipFilesMatchPinnedHashes() throws Exception {
        byte[] marker = Files.readAllBytes(ASSETS.resolve("owner.marker"));
        assertEquals(AppConstants.OVERVIEW_OWNER_SHA256, sha256(marker));
        assertEquals(AppConstants.OVERVIEW_OWNER_VALUE,
                new String(marker, StandardCharsets.UTF_8).trim());
        assertEquals(AppConstants.OVERVIEW_MODULE_PROP_SHA256,
                sha256(Files.readAllBytes(ASSETS.resolve("module.prop"))));
    }

    @Test
    public void bootScriptMatchesPinnedHashAndFixedScope() throws Exception {
        byte[] bytes = Files.readAllBytes(ASSETS.resolve("boot-completed.sh"));
        String script = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(AppConstants.OVERVIEW_BOOT_SCRIPT_SHA256, sha256(bytes));
        assertTrue(script.contains("TARGET_PACKAGE=\"com.android.launcher3\""));
        assertTrue(script.contains(AppConstants.DARK_RESOURCE));
        assertTrue(script.contains(AppConstants.LIGHT_RESOURCE));
        assertTrue(script.contains("cmd overlay enable --user 0"));
        assertFalse(script.contains("cmd overlay fabricate --user"));
        assertFalse(script.contains("curl "));
        assertFalse(script.contains("wget "));
        assertFalse(script.contains("pm disable"));
        assertFalse(script.contains("pm uninstall"));
    }

    @Test
    public void scriptUsesLfOnlyAndSkipMountIsPresent() throws Exception {
        byte[] script = Files.readAllBytes(ASSETS.resolve("boot-completed.sh"));
        for (byte value : script) {
            assertFalse("boot script must not contain CR", value == '\r');
        }
        assertTrue(Files.isRegularFile(ASSETS.resolve("skip_mount")));
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
