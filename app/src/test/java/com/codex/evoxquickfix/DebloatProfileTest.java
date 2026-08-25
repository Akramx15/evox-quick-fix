package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public final class DebloatProfileTest {
    private static final Path ASSET = Path.of(
            "src", "main", "assets", DebloatProfile.ASSET_NAME);

    @Test
    public void profileHasExactCountAndCanonicalHash() throws Exception {
        DebloatProfile profile = load();
        assertEquals(DebloatProfile.PROFILE_ID, profile.profileId);
        assertEquals(104, profile.entries.size());
        assertEquals(DebloatProfile.EXPECTED_PACKAGE_SET_SHA256,
                DebloatProfile.canonicalPackageSetSha256(
                        profile.entries.stream().map(entry -> entry.packageName).toList()));
        assertEquals("bb24e159ce264a4fc9b823051b549206cceb73bef10e366c51046d2aea46488f",
                profile.packageSetSha256);
    }

    @Test
    public void profileContainsNoDuplicatePackages() throws Exception {
        DebloatProfile profile = load();
        Set<String> packages = new HashSet<>();
        for (DebloatProfileEntry entry : profile.entries) {
            assertTrue("duplicate package: " + entry.packageName,
                    packages.add(entry.packageName));
            assertEquals(entry, profile.find(entry.packageName));
        }
        assertEquals(profile.entries.size(), packages.size());
    }

    @Test
    public void protectedPackagesAndGoogleAppAreExcluded() throws Exception {
        DebloatProfile profile = load();
        for (String protectedPackage : DebloatProfile.PROTECTED_PACKAGES) {
            assertFalse("protected package included: " + protectedPackage,
                    profile.contains(protectedPackage));
        }
        assertFalse(profile.contains("com.google.android.googlequicksearchbox"));
    }

    @Test
    public void everyPackageHasIndependentCompleteBilingualDescriptions() throws Exception {
        DebloatProfile profile = load();
        Set<String> arabicNames = new HashSet<>();
        Set<String> englishNames = new HashSet<>();
        Set<String> arabicFunctions = new HashSet<>();
        Set<String> englishFunctions = new HashSet<>();
        Set<String> arabicImpacts = new HashSet<>();
        Set<String> englishImpacts = new HashSet<>();
        for (DebloatProfileEntry entry : profile.entries) {
            assertNotNull(entry.name);
            assertNotNull(entry.function);
            assertNotNull(entry.impact);
            assertNotSame(entry.name, entry.function);
            assertNotSame(entry.name, entry.impact);
            assertNotSame(entry.function, entry.impact);
            assertArabic(entry.name.arabic);
            assertEnglish(entry.name.english);
            assertArabic(entry.function.arabic);
            assertEnglish(entry.function.english);
            assertArabic(entry.impact.arabic);
            assertEnglish(entry.impact.english);
            assertTrue(arabicNames.add(entry.name.arabic));
            assertTrue(englishNames.add(entry.name.english));
            assertTrue(arabicFunctions.add(entry.function.arabic));
            assertTrue(englishFunctions.add(entry.function.english));
            assertTrue(arabicImpacts.add(entry.impact.arabic));
            assertTrue(englishImpacts.add(entry.impact.english));
        }
        assertEquals(104, arabicNames.size());
        assertEquals(104, englishNames.size());
        assertEquals(104, arabicFunctions.size());
        assertEquals(104, englishFunctions.size());
        assertEquals(104, arabicImpacts.size());
        assertEquals(104, englishImpacts.size());
    }

    @Test
    public void dangerFlagsMatchRiskClassification() throws Exception {
        DebloatProfile profile = load();
        int dangerous = 0;
        int nonDangerous = 0;
        for (DebloatProfileEntry entry : profile.entries) {
            assertEquals(entry.risk.dangerousByDefinition, entry.dangerous);
            if (entry.dangerous) {
                dangerous++;
            } else {
                nonDangerous++;
            }
        }
        assertTrue("profile needs dangerous entries", dangerous > 0);
        assertTrue("profile needs lower-risk entries", nonDangerous > 0);
    }

    @Test
    public void declaredHashCannotBeChangedSilently() throws Exception {
        String json = new String(Files.readAllBytes(ASSET), StandardCharsets.UTF_8)
                .replace(DebloatProfile.EXPECTED_PACKAGE_SET_SHA256,
                        "0000000000000000000000000000000000000000000000000000000000000000");
        assertThrows(IllegalArgumentException.class, () -> DebloatProfile.parse(json));
    }

    @Test
    public void profileContainsNoAttributionMetadata() throws Exception {
        String json = new String(Files.readAllBytes(ASSET), StandardCharsets.UTF_8);
        assertFalse(json.contains("\"credit\":"));
        assertFalse(json.contains("\"sources\":"));
        assertFalse(json.contains("\"author\""));
        assertFalse(json.contains("\"handle\""));
    }

    private static DebloatProfile load() throws Exception {
        return DebloatProfile.parse(new String(
                Files.readAllBytes(ASSET), StandardCharsets.UTF_8));
    }

    private static void assertArabic(String value) {
        assertNotNull(value);
        assertFalse(value.isBlank());
        assertTrue(value.matches(".*[\\u0600-\\u06FF].*"));
    }

    private static void assertEnglish(String value) {
        assertNotNull(value);
        assertFalse(value.isBlank());
        assertTrue(value.matches(".*[A-Za-z].*"));
    }
}
