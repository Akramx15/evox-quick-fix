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
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

final class SnapshotStore {
    private static final int SCHEMA = 3;
    private static final int LEGACY_SCHEMA = 2;
    private final Context context;

    SnapshotStore(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized JSONObject ensureSnapshot() throws Exception {
        JSONObject existing = load();
        if (existing != null) {
            // Repair a missing root copy before allowing any mutation.
            save(existing);
            return existing;
        }
        if (hasAnySnapshotFile()) {
            throw new IllegalStateException(
                    "Snapshot موجود لكنه تالف أو يخص رومًا مختلفًا؛ لم يتم أي تغيير");
        }
        JSONObject snapshot = captureCurrent();
        save(snapshot);
        return snapshot;
    }

    synchronized JSONObject load() {
        JSONObject localCandidate = readLocalCandidate();
        int localSchema = localCandidate == null
                ? -1 : localCandidate.optInt("schema", -1);
        JSONObject local = normalizeSnapshot(localCandidate);
        if (local != null) {
            if (localSchema != SCHEMA) {
                try {
                    save(local);
                } catch (Exception migrationFailure) {
                    return null;
                }
            }
            return local;
        }

        try {
            CommandResult root = RootShell.run("cat " + AppConstants.ROOT_SNAPSHOT);
            if (root.ok() && root.output.startsWith("{")) {
                JSONObject candidate = new JSONObject(root.output);
                int rootSchema = candidate.optInt("schema", -1);
                JSONObject restored = normalizeSnapshot(candidate);
                if (restored != null) {
                    if (rootSchema != SCHEMA) {
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
        JSONObject json = new JSONObject();
        json.put("schema", SCHEMA);
        json.put("createdAt", Instant.now().toString());
        json.put("buildFingerprint", Build.FINGERPRINT);
        json.put("model", Build.MODEL);
        json.put("sdk", Build.VERSION.SDK_INT);
        UserManager users = context.getSystemService(UserManager.class);
        json.put("systemUser", users != null && users.isSystemUser());
        putNullable(json, "searchAllEntrypoints",
                settingGet("secure", "search_all_entrypoints_enabled"));
        putNullable(json, "navbarLongPress",
                settingGet("system", "navbar_long_press_gesture"));
        json.put("darkOverlayEnabled", overlayEnabled(AppConstants.DARK_OVERLAY));
        json.put("lightOverlayEnabled", overlayEnabled(AppConstants.LIGHT_OVERLAY));
        boolean overviewCurrent = testState("test -d " + AppConstants.OVERVIEW_MODULE_DIR,
                "فحص مجلد وحدة الشفافية الحالية");
        boolean overviewUpdate = testState("test -d "
                + AppConstants.OVERVIEW_MODULE_UPDATE_DIR,
                "فحص مجلد تحديث وحدة الشفافية");
        json.put("overviewModuleExisted", overviewCurrent || overviewUpdate);
        json.put("overviewCurrentExisted", overviewCurrent);
        json.put("overviewUpdateExisted", overviewUpdate);
        json.put("overviewRemoveMarked", testState("test -e "
                + AppConstants.OVERVIEW_MODULE_DIR + "/remove",
                "فحص علامة إزالة وحدة الشفافية"));
        json.put("overviewDisableMarked", testState("test -e "
                + AppConstants.OVERVIEW_MODULE_DIR + "/disable",
                "فحص علامة تعطيل وحدة الشفافية"));
        json.put("vectorModuleEnabled", vectorModuleEnabled());
        json.put("vectorScopeHadQuickSearch", vectorScopeHasQuickSearch());
        boolean circleCurrent = testState("test -d " + AppConstants.CIRCLE_MODULE_DIR,
                "فحص مجلد Circle الحالي");
        boolean circleUpdate = testState("test -d " + AppConstants.CIRCLE_MODULE_UPDATE_DIR,
                "فحص مجلد تحديث Circle");
        json.put("circleModuleExisted", circleCurrent || circleUpdate);
        json.put("circleCurrentExisted", circleCurrent);
        json.put("circleUpdateExisted", circleUpdate);
        json.put("circleRemoveMarked", testState("test -e "
                + AppConstants.CIRCLE_MODULE_DIR + "/remove", "فحص علامة إزالة Circle"));
        json.put("circleDisableMarked", testState("test -e "
                + AppConstants.CIRCLE_MODULE_DIR + "/disable", "فحص علامة تعطيل Circle"));
        json.put("disabledPackagesSha256", disabledPackagesDigest());
        return json;
    }

    private void save(JSONObject json) throws Exception {
        if (!isValidForCurrentDevice(json)) {
            throw new IllegalStateException("رفض حفظ Snapshot غير صالح للجهاز الحالي");
        }
        File local = localFile();
        writeLocalAtomically(json.toString(2));
        String source = ShellEscaper.quote(local.getAbsolutePath());
        String command = "if test -L " + AppConstants.ROOT_STATE_DIR
                + "; then exit 45; fi; mkdir -p " + AppConstants.ROOT_STATE_DIR
                + " && chown 0:0 " + AppConstants.ROOT_STATE_DIR
                + " && chmod 700 " + AppConstants.ROOT_STATE_DIR
                + " && cp " + source + " " + AppConstants.ROOT_SNAPSHOT + ".tmp"
                + " && chown 0:0 " + AppConstants.ROOT_SNAPSHOT + ".tmp"
                + " && chmod 600 " + AppConstants.ROOT_SNAPSHOT + ".tmp"
                + " && mv -f " + AppConstants.ROOT_SNAPSHOT + ".tmp "
                + AppConstants.ROOT_SNAPSHOT;
        RootShell.run(command).requireSuccess("حفظ Snapshot الجذر");
    }

    private JSONObject readLocalCandidate() {
        try {
            File local = localFile();
            if (!local.isFile()) {
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
            return isValidForCurrentDevice(json) ? json : null;
        }
        if (schema != LEGACY_SCHEMA || !isLegacyValidForCurrentDevice(json)) {
            return null;
        }
        try {
            boolean overviewPathExists = testState("test -e "
                            + AppConstants.OVERVIEW_MODULE_DIR + " -o -L "
                            + AppConstants.OVERVIEW_MODULE_DIR + " -o -e "
                            + AppConstants.OVERVIEW_MODULE_UPDATE_DIR + " -o -L "
                            + AppConstants.OVERVIEW_MODULE_UPDATE_DIR,
                    "فحص مسارات وحدة الشفافية قبل ترحيل Snapshot");
            if (overviewPathExists) {
                return null;
            }
            json.put("schema", SCHEMA);
            json.put("overviewModuleExisted", false);
            json.put("overviewCurrentExisted", false);
            json.put("overviewUpdateExisted", false);
            json.put("overviewRemoveMarked", false);
            json.put("overviewDisableMarked", false);
            return isValidForCurrentDevice(json) ? json : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isValidForCurrentDevice(JSONObject json) {
        if (json == null || json.optInt("schema", -1) != SCHEMA
                || !hasStableBaseFields(json)
                || !json.has("overviewModuleExisted")
                || !json.has("overviewCurrentExisted")
                || !json.has("overviewUpdateExisted")
                || !json.has("overviewRemoveMarked")
                || !json.has("overviewDisableMarked")) {
            return false;
        }
        return true;
    }

    private boolean isLegacyValidForCurrentDevice(JSONObject json) {
        return json != null && json.optInt("schema", -1) == LEGACY_SCHEMA
                && hasStableBaseFields(json);
    }

    private boolean hasStableBaseFields(JSONObject json) {
        if (json == null
                || !Build.FINGERPRINT.equals(json.optString("buildFingerprint", ""))
                || !Build.MODEL.equals(json.optString("model", ""))
                || json.optInt("sdk", -1) != Build.VERSION.SDK_INT
                || !json.optBoolean("systemUser", false)) {
            return false;
        }
        String disabledDigest = json.optString("disabledPackagesSha256", "");
        return json.has("searchAllEntrypoints")
                && json.has("navbarLongPress")
                && json.has("darkOverlayEnabled")
                && json.has("lightOverlayEnabled")
                && json.has("vectorModuleEnabled")
                && json.has("vectorScopeHadQuickSearch")
                && json.has("circleModuleExisted")
                && json.has("circleCurrentExisted")
                && json.has("circleUpdateExisted")
                && json.has("circleRemoveMarked")
                && json.has("circleDisableMarked")
                && disabledDigest.matches("[0-9a-f]{64}");
    }

    private boolean hasAnySnapshotFile() {
        return localFile().exists() || testState(
                "test -e " + AppConstants.ROOT_SNAPSHOT, "فحص Snapshot الجذر");
    }

    private void writeLocalAtomically(String text) throws Exception {
        File local = localFile();
        File temporary = new File(local.getParentFile(), local.getName() + ".tmp");
        writeUtf8(temporary, text);
        if (!temporary.renameTo(local)) {
            throw new IllegalStateException("تعذر تثبيت Snapshot المحلي ذريًا");
        }
    }

    private File localFile() {
        return new File(context.getFilesDir(), "snapshot.json");
    }

    private String settingGet(String namespace, String key) {
        CommandResult result = RootShell.run("settings --user 0 get " + namespace + " " + key);
        result.requireSuccess("قراءة " + key);
        if (result.output.isBlank() || "null".equals(result.output)) {
            return null;
        }
        return result.output;
    }

    private boolean overlayEnabled(String identifier) {
        CommandResult result = RootShell.run("cmd overlay list --user 0 "
                + AppConstants.LAUNCHER_PACKAGE);
        result.requireSuccess("قراءة حالة overlays");
        return result.output.lines().map(String::trim)
                .anyMatch(line -> line.equals("[x] " + identifier));
    }

    private boolean vectorModuleEnabled() {
        CommandResult result = RootShell.run(AppConstants.VECTOR_CLI + " modules ls");
        result.requireSuccess("قراءة حالة وحدة Vector");
        return result.output.lines().anyMatch(line -> line.contains(AppConstants.APP_PACKAGE)
                && line.toLowerCase(Locale.ROOT).contains("enabled"));
    }

    private boolean vectorScopeHasQuickSearch() {
        CommandResult result = RootShell.run(AppConstants.VECTOR_CLI + " scope ls "
                + AppConstants.APP_PACKAGE);
        result.requireSuccess("قراءة نطاق Vector");
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
        result.requireSuccess("قراءة الحزم المعطلة");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(result.output.getBytes(StandardCharsets.UTF_8)));
    }

    static void restoreSetting(JSONObject snapshot, String jsonKey,
                               String namespace, String settingKey) {
        if (snapshot == null || !snapshot.has(jsonKey)) {
            throw new IllegalStateException("Snapshot لا يحتوي " + jsonKey);
        }
        if (snapshot.isNull(jsonKey)) {
            RootShell.run("settings --user 0 delete " + namespace + " " + settingKey)
                    .requireSuccess("استرجاع " + settingKey);
            return;
        }
        String value = snapshot.optString(jsonKey, "");
        RootShell.run("settings --user 0 put " + namespace + " " + settingKey + " "
                + ShellEscaper.quote(value)).requireSuccess("استرجاع " + settingKey);
    }

    private static void putNullable(JSONObject json, String key, String value) throws Exception {
        if (value == null) {
            json.put(key, JSONObject.NULL);
        } else {
            json.put(key, value);
        }
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
