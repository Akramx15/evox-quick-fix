package com.codex.evoxquickfix;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

final class DebloatProfile {
    static final String ASSET_NAME = "debloat_profile_v1.json";
    static final int SCHEMA = 1;
    static final String PROFILE_ID = "evox-debloat-user0-v1";
    static final int EXPECTED_PACKAGE_COUNT = 104;
    static final String EXPECTED_PACKAGE_SET_SHA256 =
            "bb24e159ce264a4fc9b823051b549206cceb73bef10e366c51046d2aea46488f";
    static final Set<String> PROTECTED_PACKAGES = Set.of(
            "android",
            "com.android.launcher3",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.android.phone",
            "com.android.providers.media.module",
            "com.android.providers.telephony",
            "com.android.settings",
            "com.android.shell",
            "com.android.systemui",
            "com.android.vending",
            "com.codex.evoxquickfix",
            "com.google.android.gms",
            "com.google.android.googlequicksearchbox",
            "com.google.android.gsf",
            "com.tk.quicksearch");

    private static final Pattern PACKAGE_NAME = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final Pattern ARABIC_LETTER = Pattern.compile(".*[\\u0600-\\u06FF].*");
    private static final Pattern ENGLISH_LETTER = Pattern.compile(".*[A-Za-z].*");

    final String profileId;
    final List<DebloatProfileEntry> entries;
    final String packageSetSha256;
    private final Map<String, DebloatProfileEntry> byPackage;

    private DebloatProfile(String profileId, List<DebloatProfileEntry> entries,
                           String packageSetSha256) {
        this.profileId = profileId;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.packageSetSha256 = packageSetSha256;
        Map<String, DebloatProfileEntry> index = new HashMap<>();
        for (DebloatProfileEntry entry : entries) {
            index.put(entry.packageName, entry);
        }
        this.byPackage = Collections.unmodifiableMap(index);
    }

    static DebloatProfile parse(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            throw new IllegalArgumentException("Debloat profile JSON is empty");
        }
        Map<String, Object> root = object(DebloatJsonParser.parse(jsonText), "root");
        if (integer(root, "schema") != SCHEMA) {
            throw new IllegalArgumentException("Unsupported debloat profile schema");
        }
        String profileId = requiredString(root, "profileId");
        if (!PROFILE_ID.equals(profileId)) {
            throw new IllegalArgumentException("Unexpected debloat profile ID");
        }
        if (integer(root, "packageCount") != EXPECTED_PACKAGE_COUNT) {
            throw new IllegalArgumentException("Unexpected declared package count");
        }
        String declaredHash = requiredString(root, "packageSetSha256");
        if (!EXPECTED_PACKAGE_SET_SHA256.equals(declaredHash)) {
            throw new IllegalArgumentException("Unexpected declared package-set hash");
        }
        validateTarget(object(root.get("target"), "target"));
        validateProtectedDeclaration(array(root.get("excludedProtectedPackages"),
                "excludedProtectedPackages"));

        List<Object> packages = array(root.get("packages"), "packages");
        List<DebloatProfileEntry> entries = new ArrayList<>(packages.size());
        for (int index = 0; index < packages.size(); index++) {
            Map<String, Object> item = object(packages.get(index), "packages[" + index + "]");
            DebloatRisk risk = DebloatRisk.parse(requiredString(item, "risk"));
            entries.add(new DebloatProfileEntry(
                    requiredString(item, "package"),
                    localized(object(item.get("name"), "name"), "name"),
                    localized(object(item.get("function"), "function"), "function"),
                    localized(object(item.get("impact"), "impact"), "impact"),
                    risk,
                    bool(item, "dangerous")));
        }
        validateEntries(entries, declaredHash);
        return new DebloatProfile(profileId, entries, declaredHash);
    }

    DebloatProfileEntry find(String packageName) {
        return byPackage.get(packageName);
    }

    boolean contains(String packageName) {
        return byPackage.containsKey(packageName);
    }

    private static void validateTarget(Map<String, Object> target) {
        List<Object> families = array(target.get("modelFamilies"), "modelFamilies");
        List<Object> excluded = array(target.get("excludedModels"), "excludedModels");
        if (!families.equals(List.of("SM-S911*", "SM-S916*", "SM-S918*"))
                || !excluded.equals(List.of("SM-S711*"))
                || integer(target, "sdk") != 36
                || integer(target, "user") != 0
                || !"KernelSU".equals(requiredString(target, "root"))) {
            throw new IllegalArgumentException("Unexpected debloat profile target");
        }
    }

    private static void validateProtectedDeclaration(List<Object> declared) {
        Set<String> values = new HashSet<>();
        for (Object value : declared) {
            if (!(value instanceof String packageName) || packageName.isBlank()) {
                throw new IllegalArgumentException("Invalid protected-package declaration");
            }
            values.add(packageName);
        }
        if (!PROTECTED_PACKAGES.equals(values) || values.size() != declared.size()) {
            throw new IllegalArgumentException("Protected-package declaration is inconsistent");
        }
    }

    static void validateEntries(List<DebloatProfileEntry> entries, String expectedHash) {
        if (entries == null || entries.size() != EXPECTED_PACKAGE_COUNT) {
            throw new IllegalArgumentException("Debloat profile must contain exactly 104 entries");
        }
        Set<String> seen = new HashSet<>();
        for (DebloatProfileEntry entry : entries) {
            if (entry == null || !PACKAGE_NAME.matcher(entry.packageName).matches()) {
                throw new IllegalArgumentException("Invalid package name in debloat profile");
            }
            if (!seen.add(entry.packageName)) {
                throw new IllegalArgumentException("Duplicate package: " + entry.packageName);
            }
            if (PROTECTED_PACKAGES.contains(entry.packageName)) {
                throw new IllegalArgumentException("Protected package included: " + entry.packageName);
            }
            validateLocalized(entry.name, entry.packageName + ".name");
            validateLocalized(entry.function, entry.packageName + ".function");
            validateLocalized(entry.impact, entry.packageName + ".impact");
            if (entry.risk == null || entry.dangerous != entry.risk.dangerousByDefinition) {
                throw new IllegalArgumentException(
                        "Danger flag does not match risk for " + entry.packageName);
            }
        }
        String actualHash = canonicalPackageSetSha256(seen);
        if (!EXPECTED_PACKAGE_SET_SHA256.equals(expectedHash)
                || !EXPECTED_PACKAGE_SET_SHA256.equals(actualHash)) {
            throw new IllegalArgumentException("Debloat package-set hash mismatch");
        }
    }

    static String canonicalPackageSetSha256(Collection<String> packageNames) {
        try {
            String canonical = String.join("\n", new TreeSet<>(packageNames));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static DebloatLocalizedText localized(Map<String, Object> object, String field) {
        String arabic = requiredString(object, "ar");
        String english = requiredString(object, "en");
        requireArabic(arabic, field + ".ar");
        requireEnglish(english, field + ".en");
        return new DebloatLocalizedText(arabic, english);
    }

    private static void validateLocalized(DebloatLocalizedText value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Missing localized text: " + field);
        }
        requireArabic(value.arabic, field + ".ar");
        requireEnglish(value.english, field + ".en");
    }

    private static String requiredString(Map<String, Object> object, String key) {
        Object raw = object.get(key);
        if (!(raw instanceof String stringValue) || stringValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing string field: " + key);
        }
        return stringValue.trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected object field: " + field);
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String field) {
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("Expected array field: " + field);
        }
        return (List<Object>) value;
    }

    private static int integer(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Number number)
                || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("Expected integer field: " + key);
        }
        return number.intValue();
    }

    private static boolean bool(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Boolean booleanValue)) {
            throw new IllegalArgumentException("Expected boolean field: " + key);
        }
        return booleanValue;
    }

    private static void requireArabic(String value, String field) {
        if (value == null || value.isBlank() || !ARABIC_LETTER.matcher(value).matches()) {
            throw new IllegalArgumentException("Arabic text is incomplete: " + field);
        }
    }

    private static void requireEnglish(String value, String field) {
        if (value == null || value.isBlank() || !ENGLISH_LETTER.matcher(value).matches()) {
            throw new IllegalArgumentException("English text is incomplete: " + field);
        }
    }
}
