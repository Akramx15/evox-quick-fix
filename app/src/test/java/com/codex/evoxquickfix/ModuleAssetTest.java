package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.Test;

public final class ModuleAssetTest {
    private static final Path ASSETS = Path.of("src", "main", "assets", "circle_module");

    @Test
    public void ownershipMarkerMatchesPinnedHashAndValue() throws Exception {
        byte[] marker = Files.readAllBytes(ASSETS.resolve("owner.marker"));
        assertEquals(AppConstants.CIRCLE_OWNER_SHA256, sha256(marker));
        assertEquals(AppConstants.CIRCLE_OWNER_VALUE,
                new String(marker, StandardCharsets.UTF_8).trim());
    }

    @Test
    public void modulePropMatchesPinnedHash() throws Exception {
        assertEquals(AppConstants.CIRCLE_MODULE_PROP_SHA256,
                sha256(Files.readAllBytes(ASSETS.resolve("module.prop"))));
    }

    @Test
    public void xmlDeclaresOnlyTheExpectedFeature() throws Exception {
        String xml = Files.readString(
                ASSETS.resolve("contextual_search_feature.xml"), StandardCharsets.UTF_8);
        assertTrue(xml.contains("name=\"" + AppConstants.CONTEXTUAL_FEATURE + "\""));
        assertEquals(1, xml.split("<feature ", -1).length - 1);
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
