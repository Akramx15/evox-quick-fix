package com.codex.evoxquickfix;

import android.content.Context;
import android.os.Build;
import android.os.UserManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class SnapshotStore {
    static final int SCHEMA = 4;
    private static final int PREVIOUS_SCHEMA = 3;
    private static final int LEGACY_SCHEMA = 2;
    private static final String FEATURE_BASELINES = "featureBaselines";
    private final Context context;

    enum Feature {
        TRANSPARENCY("transparency"),
        BACK("back"),
        CIRCLE("circle");

        final String key;

        Feature(String key) {
            this.key = key;
        }
    }

    SnapshotStore(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized JSONObject ensureSnapshot() throws Exception {
        JSONObject existing = load();
        if (existing != null) {
            save(existing);
            return existing;
        }
        if (hasAnySnapshotFile()) {
            throw new IllegalStateException(
                    "Snapshot exists but is invalid or belongs to another ROM build");
        }
        JSONObject snapshot = captureCurrent();
        save(snapshot);
        return snapshot;
    }

    synchronized JSONObject load() {
        JSONObject localCandidate = readLocalCandidate();
        int localSchema = localCandidate == null ? -1 : localCandidate.optInt("schema", -1);
        JSONObject local = normalizeSnapshot(localCandidate);
        if (local != null) {
            if (localSchema != SCHEMA || !hasValidSchemaShape(localCandidate)) {
                try {
                    save(local);
                } catch (Exception migrationFailure) {
                    return null;
                }
            }
            return local;
        }

        try {
            CommandResult root = RootShell.run(
                    "if test -L " + AppConstants.ROOT_SNAPSHOT
                            + "; then exit 45; fi; cat " + AppConstants.ROOT_SNAPSHOT);
            if (root.ok() && root.output.startsWith("{")) {
                JSONObject candidate = new JSONObject(root.output);
                int rootSchema = candidate.optInt("schema", -1);
                JSONObject restored = normalizeSnapshot(candidate);
                if (restored != null) {
                    if (rootSchema != SCHEMA || !hasValidSchemaShape(candidate)) {
                        save(restored);
                    } else {
                        writeLocalAtomically(restored.toString(2));
                    }
                    return restored;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    synchronized JSONObject captureCurrent() throws Exception {
        return captureCurrent(false, false, false);
    }

    synchronized JSONObject captureCurrent(boolean requireTransparency,
                                            boolean requireBack,
                                            boolean requireCircle) throws Exception {
        JSONObject json = baseSnapshot();
        json.put("transparency", captureTransparency(requireTransparency));
        json.put("back", captureBack(requireBack));
        json.put("circle", captureCircle(requireCircle));
        json.put(FEATURE_BASELINES, new JSONObject());
        json.put("disabledPackagesSha256", disabledPackagesDigest());
        return json;
    }

    synchronized JSONObject ensureFeatureBaseline(Feature feature,
                                                   boolean alreadyManaged) throws Exception {
        JSONObject snapshot = ensureSnapshot();
        JSONObject baselines = snapshot.optJSONObject(FEATURE_BASELINES);
        if (baselines == null) {
            baselines = new JSONObject();
            snapshot.put(FEATURE_BASELINES, baselines);
        }
        JSONObject existing = baselines.optJSONObject(feature.key);
        if (existing != null) {
            requireValidFeatureSection(feature, existing, true);
            return existing;
        }
        if (alreadyManaged) {
            JSONObject legacy = snapshot.optJSONObject(feature.key);
            requireValidFeatureSection(feature, legacy, true);
            return legacy;
        }
        JSONObject captured = captureFeatureStrict(feature);
        baselines.put(feature.key, captured);
        save(snapshot);
        return captured;
    }

    synchronized boolean hasFeatureBaseline(JSONObject snapshot, Feature feature) {
        JSONObject baselines = snapshot == null
                ? null : snapshot.optJSONObject(FEATURE_BASELINES);
        return baselines != null && baselines.optJSONObject(feature.key) != null;
    }

    synchronized JSONObject featureTarget(JSONObject snapshot, Feature feature) {
        if (snapshot == null) {
            throw new IllegalStateException("Snapshot is missing");
        }
        JSONObject baselines = snapshot.optJSONObject(FEATURE_BASELINES);
        JSONObject target = baselines == null ? null : baselines.optJSONObject(feature.key);
        if (target == null) {
            target = snapshot.optJSONObject(feature.key);
        }
        requireValidFeatureSection(feature, target, true);
        return target;
    }

    private JSONObject captureFeatureStrict(Feature feature) throws Exception {
        return switch (feature) {
            case TRANSPARENCY -> captureTransparency(true);
            case BACK -> captureBack(true);
            case CIRCLE -> captureCircle(true);
        };
    }

    private JSONObject captureTransparency(boolean required) throws Exception {
        JSONObject section = new JSONObject();
        boolean captured = true;
        Boolean dark = null;
        Boolean light = null;
        try {
            dark = overlayEnabledStrict(AppConstants.DARK_OVERLAY);
            light = overlayEnabledStrict(AppConstants.LIGHT_OVERLAY);
        } catch (Exception failure) {
            if (required) {
                throw failure;
            }
            captured = false;
        }
        section.put("stateCaptured", captured);
        section.put("darkOverlayEnabled", Boolean.TRUE.equals(dark));
        section.put("lightOverlayEnabled", Boolean.TRUE.equals(light));
        boolean overviewCurrent = testState("test -d " + AppConstants.OVERVIEW_MODULE_DIR,
                "inspect current overview module");
        boolean overviewUpdate = testState("test -d "
                + AppConstants.OVERVIEW_MODULE_UPDATE_DIR, "inspect overview module update");
        section.put("overviewModuleExisted", overviewCurrent || overviewUpdate);
        section.put("overviewCurrentExisted", overviewCurrent);
        section.put("overviewUpdateExisted", overviewUpdate);
        section.put("overviewRemoveMarked", testState("test -e "
                + AppConstants.OVERVIEW_MODULE_DIR + "/remove", "inspect overview remove flag"));
        section.put("overviewDisableMarked", testState("test -e "
                + AppConstants.OVERVIEW_MODULE_DIR + "/disable", "inspect overview disable flag"));
        return section;
    }

    private JSONObject captureBack(boolean required) throws Exception {
        JSONObject section = new JSONObject();
        CommandResult executable = RootShell.run("test -x " + AppConstants.VECTOR_CLI);
        if (!executable.timedOut && executable.exitCode == 1) {
            section.put("stateCaptured", true);
            section.put("vectorAvailable", false);
            section.put("vectorModuleEnabled", false);
            section.put("vectorScopeHadQuickSearch", false);
            return section;
        }
        executable.requireSuccess("inspect Vector executable");
        try {
            boolean moduleEnabled = vectorModuleEnabledStrict();
            boolean scopePresent = vectorScopeHasQuickSearchStrict();
            section.put("stateCaptured", true);
            section.put("vectorAvailable", true);
            section.put("vectorModuleEnabled", moduleEnabled);
            section.put("vectorScopeHadQuickSearch", scopePresent);
        } catch (Exception failure) {
            if (required) {
                throw failure;
            }
            section.put("stateCaptured", false);
            section.put("vectorAvailable", true);
            section.put("vectorModuleEnabled", false);
            section.put("vectorScopeHadQuickSearch", false);
        }
        return section;
    }

    private JSONObject captureCircle(boolean required) throws Exception {
        JSONObject section = new JSONObject();
        boolean captured = true;
        String search = null;
        String navbar = null;
        try {
            search = settingGet("secure", "search_all_entrypoints_enabled");
            navbar = settingGet("system", "navbar_long_press_gesture");
        } catch (Exception failure) {
            if (required) {
                throw failure;
            }
            captured = false;
        }
        section.put("stateCaptured", captured);
        putNullable(section, "searchAllEntrypoints", search);
        putNullable(section, "navbarLongPress", navbar);
        boolean circleCurrent = testState("test -d " + AppConstants.CIRCLE_MODULE_DIR,
                "inspect current Circle module");
        boolean circleUpdate = testState("test -d " + AppConstants.CIRCLE_MODULE_UPDATE_DIR,
                "inspect Circle module update");
        section.put("circleModuleExisted", circleCurrent || circleUpdate);
        section.put("circleCurrentExisted", circleCurrent);
        section.put("circleUpdateExisted", circleUpdate);
        section.put("circleRemoveMarked", testState("test -e "
                + AppConstants.CIRCLE_MODULE_DIR + "/remove", "inspect Circle remove flag"));
        section.put("circleDisableMarked", testState("test -e "
                + AppConstants.CIRCLE_MODULE_DIR + "/disable", "inspect Circle disable flag"));
        return section;
    }

    private JSONObject baseSnapshot() throws Exception {
        JSONObject json = new JSONObject();
        json.put("schema", SCHEMA);
        json.put("createdAt", Instant.now().toString());
        json.put("buildFingerprint", Build.FINGERPRINT);
        json.put("model", Build.MODEL);
        json.put("sdk", Build.VERSION.SDK_INT);
        UserManager users = context.getSystemService(UserManager.class);
        json.put("systemUser", users != null && users.isSystemUser());
        return json;
    }

    private void save(JSONObject json) throws Exception {
        if (!isValidForCurrentDevice(json)) {
            throw new IllegalStateException("Refused to save an invalid device snapshot");
        }
        File local = localFile();
        writeLocalAtomically(json.toString(2));
        String source = ShellEscaper.quote(local.getAbsolutePath());
        String command = "if test -L " + AppConstants.ROOT_STATE_DIR
                + " -o -L " + AppConstants.ROOT_SNAPSHOT + "; then exit 45; fi; "
                + "mkdir -p " + AppConstants.ROOT_STATE_DIR
                + " && chown 0:0 " + AppConstants.ROOT_STATE_DIR
                + " && chmod 700 " + AppConstants.ROOT_STATE_DIR
                + " && cp " + source + " " + AppConstants.ROOT_SNAPSHOT + ".tmp"
                + " && chown 0:0 " + AppConstants.ROOT_SNAPSHOT + ".tmp"
                + " && chmod 600 " + AppConstants.ROOT_SNAPSHOT + ".tmp"
                + " && mv -f " + AppConstants.ROOT_SNAPSHOT + ".tmp "
                + AppConstants.ROOT_SNAPSHOT;
        RootShell.run(command).requireSuccess("save root snapshot");
    }

    private JSONObject readLocalCandidate() {
        try {
            File local = localFile();
            if (!local.isFile() || Files.isSymbolicLink(local.toPath())) {
                return null;
            }
            return new JSONObject(readUtf8(local));
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONObject normalizeSnapshot(JSONObject json) {
        if (json == null) {
            return null;
        }
        int schema = json.optInt("schema", -1);
        if (schema == SCHEMA) {
            if (isValidForCurrentDevice(json)) {
                return json;
            }
            if (!isNestedSnapshotValidForCurrentDevice(json)) {
                return null;
            }
            JSONObject migrated = migrateNestedSchema4(json);
            return isValidForCurrentDevice(migrated) ? migrated : null;
        }
        if (schema == PREVIOUS_SCHEMA && isFlatSnapshotValid(json, true)) {
            JSONObject migrated = migrateFlatSchema(json);
            return isValidForCurrentDevice(migrated) ? migrated : null;
        }
        if (schema == LEGACY_SCHEMA && isFlatSnapshotValid(json, false)) {
            try {
                boolean overviewPathExists = testState("test -e "
                                + AppConstants.OVERVIEW_MODULE_DIR + " -o -L "
                                + AppConstants.OVERVIEW_MODULE_DIR + " -o -e "
                                + AppConstants.OVERVIEW_MODULE_UPDATE_DIR + " -o -L "
                                + AppConstants.OVERVIEW_MODULE_UPDATE_DIR,
                        "inspect overview paths before legacy migration");
                if (overviewPathExists) {
                    return null;
                }
                json.put("overviewModuleExisted", false);
                json.put("overviewCurrentExisted", false);
                json.put("overviewUpdateExisted", false);
                json.put("overviewRemoveMarked", false);
                json.put("overviewDisableMarked", false);
                JSONObject migrated = migrateFlatSchema(json);
                return isValidForCurrentDevice(migrated) ? migrated : null;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    static JSONObject migrateNestedSchema4(JSONObject nested) {
        try {
            Map<String, Object> migrated = SnapshotSchema.migrateNestedSchema4(toMap(nested));
            return migrated == null ? null : fromMap(migrated);
        } catch (Exception ignored) {
            return null;
        }
    }

    static JSONObject migrateFlatSchema(JSONObject flat) {
        try {
            Map<String, Object> migrated = SnapshotSchema.migrateFlat(toMap(flat));
            return migrated == null ? null : fromMap(migrated);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isValidForCurrentDevice(JSONObject json) {
        return hasStableBaseFields(json, SCHEMA) && hasValidSchemaShape(json);
    }

    private boolean isNestedSnapshotValidForCurrentDevice(JSONObject json) {
        return hasStableBaseFields(json, SCHEMA) && isNestedSchema4Shape(json);
    }

    private boolean isFlatSnapshotValid(JSONObject json, boolean requireOverview) {
        if (!hasStableFlatBase(json)) {
            return false;
        }
        return !requireOverview || hasAll(json, "overviewModuleExisted",
                "overviewCurrentExisted", "overviewUpdateExisted",
                "overviewRemoveMarked", "overviewDisableMarked");
    }

    private boolean hasStableBaseFields(JSONObject json, int schema) {
        return hasStrictBaseShape(json, schema)
                && Build.FINGERPRINT.equals(json.optString("buildFingerprint", ""))
                && Build.MODEL.equals(json.optString("model", ""))
                && json.optInt("sdk", -1) == Build.VERSION.SDK_INT
                && json.optBoolean("systemUser", false);
    }

    private boolean hasStableFlatBase(JSONObject json) {
        return isFlatSchemaShape(json, json != null
                && json.optInt("schema", -1) == PREVIOUS_SCHEMA)
                && Build.FINGERPRINT.equals(json.optString("buildFingerprint", ""))
                && Build.MODEL.equals(json.optString("model", ""))
                && json.optInt("sdk", -1) == Build.VERSION.SDK_INT
                && json.optBoolean("systemUser", false);
    }

    static boolean hasValidSchemaShape(JSONObject json) {
        return SnapshotSchema.validSchema4(toMap(json));
    }

    private static boolean isNestedSchema4Shape(JSONObject json) {
        return SnapshotSchema.validNestedSchema4(toMap(json));
    }

    private static boolean isFlatSchemaShape(JSONObject json, boolean requireOverview) {
        return SnapshotSchema.validFlat(json == null ? null : toMap(json), requireOverview);
    }

    private static boolean hasStrictBaseShape(JSONObject json, int schema) {
        return json != null
                && json.opt("schema") instanceof Number
                && json.optInt("schema", -1) == schema
                && json.opt("createdAt") instanceof String
                && json.opt("buildFingerprint") instanceof String
                && json.opt("model") instanceof String
                && json.opt("sdk") instanceof Number
                && json.opt("systemUser") instanceof Boolean
                && json.optBoolean("systemUser", false)
                && json.opt("disabledPackagesSha256") instanceof String
                && json.optString("disabledPackagesSha256", "").matches("[0-9a-f]{64}");
    }

    private static boolean validTransparency(JSONObject section, boolean requireCaptured) {
        return section != null
                && section.opt("stateCaptured") instanceof Boolean
                && (!requireCaptured || section.optBoolean("stateCaptured", false))
                && booleans(section, "darkOverlayEnabled", "lightOverlayEnabled",
                "overviewModuleExisted", "overviewCurrentExisted", "overviewUpdateExisted",
                "overviewRemoveMarked", "overviewDisableMarked");
    }

    private static boolean validBack(JSONObject section, boolean requireCaptured) {
        return section != null
                && section.opt("stateCaptured") instanceof Boolean
                && (!requireCaptured || section.optBoolean("stateCaptured", false))
                && booleans(section, "vectorAvailable", "vectorModuleEnabled",
                "vectorScopeHadQuickSearch");
    }

    private static boolean validCircle(JSONObject section, boolean requireCaptured) {
        return section != null
                && section.opt("stateCaptured") instanceof Boolean
                && (!requireCaptured || section.optBoolean("stateCaptured", false))
                && nullableString(section, "searchAllEntrypoints")
                && nullableString(section, "navbarLongPress")
                && booleans(section, "circleModuleExisted", "circleCurrentExisted",
                "circleUpdateExisted", "circleRemoveMarked", "circleDisableMarked");
    }

    private static boolean validTransparencyV4(JSONObject section) {
        return section != null && booleans(section, "darkOverlayEnabled",
                "lightOverlayEnabled", "overviewModuleExisted", "overviewCurrentExisted",
                "overviewUpdateExisted", "overviewRemoveMarked", "overviewDisableMarked");
    }

    private static boolean validBackV4(JSONObject section) {
        return section != null && booleans(section, "vectorAvailable",
                "vectorModuleEnabled", "vectorScopeHadQuickSearch");
    }

    private static boolean validCircleV4(JSONObject section) {
        return section != null
                && nullableString(section, "searchAllEntrypoints")
                && nullableString(section, "navbarLongPress")
                && booleans(section, "circleModuleExisted", "circleCurrentExisted",
                "circleUpdateExisted", "circleRemoveMarked", "circleDisableMarked");
    }

    private static boolean validFeatureSection(Feature feature, JSONObject section,
                                               boolean requireCaptured) {
        return switch (feature) {
            case TRANSPARENCY -> validTransparency(section, requireCaptured);
            case BACK -> validBack(section, requireCaptured);
            case CIRCLE -> validCircle(section, requireCaptured);
        };
    }

    private static void requireValidFeatureSection(Feature feature, JSONObject section,
                                                   boolean requireCaptured) {
        if (!validFeatureSection(feature, section, requireCaptured)) {
            throw new IllegalStateException("No trustworthy " + feature.key
                    + " baseline is available");
        }
    }

    private static boolean nullableString(JSONObject json, String key) {
        return json.has(key) && (json.isNull(key) || json.opt(key) instanceof String);
    }

    private static boolean booleans(JSONObject json, String... keys) {
        for (String key : keys) {
            if (!(json.opt(key) instanceof Boolean)) {
                return false;
            }
        }
        return true;
    }

    private static void copyBase(JSONObject from, JSONObject to) throws Exception {
        to.put("createdAt", from.getString("createdAt"));
        to.put("buildFingerprint", from.getString("buildFingerprint"));
        to.put("model", from.getString("model"));
        to.put("sdk", from.getInt("sdk"));
        to.put("systemUser", from.getBoolean("systemUser"));
        to.put("disabledPackagesSha256", from.getString("disabledPackagesSha256"));
    }

    static Map<String, Object> toMap(JSONObject source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = source.opt(key);
            if (value == null || value == JSONObject.NULL) {
                result.put(key, null);
            } else if (value instanceof JSONObject object) {
                result.put(key, toMap(object));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    static JSONObject fromMap(Map<String, Object> source) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            result.put(entry.getKey(), value instanceof Map<?, ?> nested
                    ? fromMap(stringMap(nested))
                    : (value == null ? JSONObject.NULL : value));
        }
        return result;
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Snapshot map key is not a string");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static boolean hasAll(JSONObject json, String... keys) {
        for (String key : keys) {
            if (!json.has(key)) {
                return false;
            }
        }
        return true;
    }

    private static void copy(JSONObject from, JSONObject to, String key) throws Exception {
        to.put(key, from.isNull(key) ? JSONObject.NULL : from.get(key));
    }

    private boolean hasAnySnapshotFile() {
        return localFile().exists() || testState(
                "test -e " + AppConstants.ROOT_SNAPSHOT + " -o -L "
                        + AppConstants.ROOT_SNAPSHOT, "inspect root snapshot");
    }

    private void writeLocalAtomically(String text) throws Exception {
        File local = localFile();
        File temporary = new File(local.getParentFile(), local.getName() + ".tmp");
        if (Files.isSymbolicLink(local.toPath()) || Files.isSymbolicLink(temporary.toPath())) {
            throw new IllegalStateException("Unsafe local snapshot symlink");
        }
        writeUtf8(temporary, text);
        try {
            Files.move(temporary.toPath(), local.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception unsupportedAtomicMove) {
            Files.move(temporary.toPath(), local.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File localFile() {
        return new File(context.getFilesDir(), "snapshot.json");
    }

    private String settingGet(String namespace, String key) {
        CommandResult result = RootShell.run("settings --user 0 get " + namespace + " " + key);
        result.requireSuccess("read " + key);
        if (result.output.isBlank() || "null".equals(result.output)) {
            return null;
        }
        return result.output;
    }

    private boolean overlayEnabledStrict(String identifier) {
        CommandResult result = RootShell.run("cmd overlay list --user 0 "
                + AppConstants.LAUNCHER_PACKAGE);
        result.requireSuccess("read overlay state for " + identifier);
        return result.output.lines().map(String::trim)
                .anyMatch(line -> line.equals("[x] " + identifier));
    }

    private boolean vectorModuleEnabledStrict() {
        CommandResult result = RootShell.run(AppConstants.VECTOR_CLI + " modules ls");
        result.requireSuccess("read Vector module state");
        return result.output.lines()
                .anyMatch(line -> line.contains(AppConstants.APP_PACKAGE)
                        && line.toLowerCase(Locale.ROOT).contains("enabled"));
    }

    private boolean vectorScopeHasQuickSearchStrict() {
        CommandResult result = RootShell.run(AppConstants.VECTOR_CLI + " scope ls "
                + AppConstants.APP_PACKAGE);
        result.requireSuccess("read Vector scope state");
        return result.output.contains(AppConstants.QUICK_SEARCH_PACKAGE);
    }

    private static boolean testState(String command, String description) {
        CommandResult result = RootShell.run(command);
        if (!result.timedOut && result.exitCode == 1) {
            return false;
        }
        result.requireSuccess(description);
        return true;
    }

    String disabledPackagesDigest() throws Exception {
        CommandResult result = RootShell.run("pm list packages -d --user 0 | sort");
        result.requireSuccess("read disabled packages");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(result.output.getBytes(StandardCharsets.UTF_8)));
    }

    static void restoreSetting(JSONObject section, String jsonKey,
                               String namespace, String settingKey) {
        if (section == null || !section.has(jsonKey)) {
            throw new IllegalStateException("Snapshot section is missing " + jsonKey);
        }
        if (section.isNull(jsonKey)) {
            RootShell.run("settings --user 0 delete " + namespace + " " + settingKey)
                    .requireSuccess("restore " + settingKey);
            return;
        }
        String value = section.optString(jsonKey, "");
        RootShell.run("settings --user 0 put " + namespace + " " + settingKey + " "
                + ShellEscaper.quote(value)).requireSuccess("restore " + settingKey);
    }

    private static void putNullable(JSONObject json, String key, String value) throws Exception {
        json.put(key, value == null ? JSONObject.NULL : value);
    }

    private static String readUtf8(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeUtf8(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }
}

/** Pure schema model used by production adapters and host-JVM migration tests. */
final class SnapshotSchema {
    private static final int SCHEMA_4 = 4;
    private static final int SCHEMA_3 = 3;
    private static final int SCHEMA_2 = 2;

    private SnapshotSchema() {}

    static Map<String, Object> migrateNestedSchema4(Map<String, Object> source) {
        if (!validNestedSchema4(source)) {
            return null;
        }
        Map<String, Object> result = baseCopy(source, SCHEMA_4);
        Map<String, Object> transparency = deepCopy(object(source, "transparency"));
        Map<String, Object> back = deepCopy(object(source, "back"));
        Map<String, Object> circle = deepCopy(object(source, "circle"));
        // Schema 4 converted failed overlay/Vector reads into false; keep them explicitly
        // untrusted until a strict per-feature baseline is captured.
        transparency.put("stateCaptured", false);
        back.put("stateCaptured", false);
        circle.put("stateCaptured", true);
        result.put("transparency", transparency);
        result.put("back", back);
        result.put("circle", circle);
        result.put("featureBaselines", new LinkedHashMap<String, Object>());
        return result;
    }

    static Map<String, Object> migrateFlat(Map<String, Object> source) {
        int schema = integer(source, "schema", -1);
        if (!validFlat(source, schema == SCHEMA_3)) {
            return null;
        }
        Map<String, Object> result = baseCopy(source, SCHEMA_4);

        Map<String, Object> transparency = new LinkedHashMap<>();
        transparency.put("stateCaptured", true);
        copyKeys(source, transparency, "darkOverlayEnabled", "lightOverlayEnabled");
        if (schema == SCHEMA_2) {
            transparency.put("overviewModuleExisted", false);
            transparency.put("overviewCurrentExisted", false);
            transparency.put("overviewUpdateExisted", false);
            transparency.put("overviewRemoveMarked", false);
            transparency.put("overviewDisableMarked", false);
        } else {
            copyKeys(source, transparency, "overviewModuleExisted", "overviewCurrentExisted",
                    "overviewUpdateExisted", "overviewRemoveMarked", "overviewDisableMarked");
        }
        result.put("transparency", transparency);

        Map<String, Object> back = new LinkedHashMap<>();
        back.put("stateCaptured", true);
        back.put("vectorAvailable", true);
        copyKeys(source, back, "vectorModuleEnabled", "vectorScopeHadQuickSearch");
        result.put("back", back);

        Map<String, Object> circle = new LinkedHashMap<>();
        circle.put("stateCaptured", true);
        copyKeys(source, circle, "searchAllEntrypoints", "navbarLongPress",
                "circleModuleExisted", "circleCurrentExisted", "circleUpdateExisted",
                "circleRemoveMarked", "circleDisableMarked");
        result.put("circle", circle);
        result.put("featureBaselines", new LinkedHashMap<String, Object>());
        return result;
    }

    static boolean validSchema4(Map<String, Object> root) {
        if (!validBase(root, SCHEMA_4)
                || !validTransparency(object(root, "transparency"), false)
                || !validBack(object(root, "back"), false)
                || !validCircle(object(root, "circle"), false)) {
            return false;
        }
        Map<String, Object> baselines = object(root, "featureBaselines");
        if (baselines == null) {
            return false;
        }
        for (Map.Entry<String, Object> entry : baselines.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?>)) {
                return false;
            }
            Map<String, Object> section = object(baselines, entry.getKey());
            boolean valid = switch (entry.getKey()) {
                case "transparency" -> validTransparency(section, true);
                case "back" -> validBack(section, true);
                case "circle" -> validCircle(section, true);
                default -> false;
            };
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    static boolean validNestedSchema4(Map<String, Object> root) {
        return validBase(root, SCHEMA_4)
                && validTransparency4(object(root, "transparency"))
                && validBack4(object(root, "back"))
                && validCircle4(object(root, "circle"));
    }

    static boolean validFlat(Map<String, Object> root, boolean requireOverview) {
        int schema = integer(root, "schema", -1);
        if ((schema != SCHEMA_3 && schema != SCHEMA_2) || !validBase(root, schema)
                || !nullableString(root, "searchAllEntrypoints")
                || !nullableString(root, "navbarLongPress")
                || !booleans(root, "darkOverlayEnabled", "lightOverlayEnabled",
                "vectorModuleEnabled", "vectorScopeHadQuickSearch", "circleModuleExisted",
                "circleCurrentExisted", "circleUpdateExisted", "circleRemoveMarked",
                "circleDisableMarked")) {
            return false;
        }
        return !requireOverview || booleans(root, "overviewModuleExisted",
                "overviewCurrentExisted", "overviewUpdateExisted",
                "overviewRemoveMarked", "overviewDisableMarked");
    }

    private static boolean validBase(Map<String, Object> root, int schema) {
        return root != null && integer(root, "schema", -1) == schema
                && root.get("createdAt") instanceof String
                && root.get("buildFingerprint") instanceof String
                && root.get("model") instanceof String
                && root.get("sdk") instanceof Number
                && Boolean.TRUE.equals(root.get("systemUser"))
                && root.get("disabledPackagesSha256") instanceof String hash
                && hash.matches("[0-9a-f]{64}");
    }

    private static boolean validTransparency(Map<String, Object> section,
                                             boolean requireCaptured) {
        return section != null && section.get("stateCaptured") instanceof Boolean
                && (!requireCaptured || Boolean.TRUE.equals(section.get("stateCaptured")))
                && booleans(section, "darkOverlayEnabled", "lightOverlayEnabled",
                "overviewModuleExisted", "overviewCurrentExisted", "overviewUpdateExisted",
                "overviewRemoveMarked", "overviewDisableMarked");
    }

    private static boolean validBack(Map<String, Object> section, boolean requireCaptured) {
        return section != null && section.get("stateCaptured") instanceof Boolean
                && (!requireCaptured || Boolean.TRUE.equals(section.get("stateCaptured")))
                && booleans(section, "vectorAvailable", "vectorModuleEnabled",
                "vectorScopeHadQuickSearch");
    }

    private static boolean validCircle(Map<String, Object> section, boolean requireCaptured) {
        return section != null && section.get("stateCaptured") instanceof Boolean
                && (!requireCaptured || Boolean.TRUE.equals(section.get("stateCaptured")))
                && nullableString(section, "searchAllEntrypoints")
                && nullableString(section, "navbarLongPress")
                && booleans(section, "circleModuleExisted", "circleCurrentExisted",
                "circleUpdateExisted", "circleRemoveMarked", "circleDisableMarked");
    }

    private static boolean validTransparency4(Map<String, Object> section) {
        return section != null && booleans(section, "darkOverlayEnabled",
                "lightOverlayEnabled", "overviewModuleExisted", "overviewCurrentExisted",
                "overviewUpdateExisted", "overviewRemoveMarked", "overviewDisableMarked");
    }

    private static boolean validBack4(Map<String, Object> section) {
        return section != null && booleans(section, "vectorAvailable",
                "vectorModuleEnabled", "vectorScopeHadQuickSearch");
    }

    private static boolean validCircle4(Map<String, Object> section) {
        return section != null && nullableString(section, "searchAllEntrypoints")
                && nullableString(section, "navbarLongPress")
                && booleans(section, "circleModuleExisted", "circleCurrentExisted",
                "circleUpdateExisted", "circleRemoveMarked", "circleDisableMarked");
    }

    private static boolean nullableString(Map<String, Object> map, String key) {
        return map != null && map.containsKey(key)
                && (map.get(key) == null || map.get(key) instanceof String);
    }

    private static boolean booleans(Map<String, Object> map, String... keys) {
        if (map == null) {
            return false;
        }
        for (String key : keys) {
            if (!(map.get(key) instanceof Boolean)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> map, String key) {
        if (map == null || !(map.get(key) instanceof Map<?, ?> value)) {
            return null;
        }
        return (Map<String, Object>) value;
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static Map<String, Object> baseCopy(Map<String, Object> source, int schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", schema);
        copyKeys(source, result, "createdAt", "buildFingerprint", "model", "sdk",
                "systemUser", "disabledPackagesSha256");
        return result;
    }

    private static void copyKeys(Map<String, Object> source, Map<String, Object> target,
                                 String... keys) {
        for (String key : keys) {
            target.put(key, source.get(key));
        }
    }

    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            result.put(entry.getKey(), value instanceof Map<?, ?> nested
                    ? deepCopy(castMap(nested)) : value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }
}
