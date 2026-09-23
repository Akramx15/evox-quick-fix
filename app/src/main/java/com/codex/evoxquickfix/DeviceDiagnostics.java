package com.codex.evoxquickfix;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class DeviceDiagnostics {
    private static final Pattern SUPPORTED_MODEL =
            Pattern.compile("^SM-S91(?:1|6|8)[A-Z0-9]*$", Pattern.CASE_INSENSITIVE);

    private final Context context;

    DeviceDiagnostics(Context context) {
        this.context = context.getApplicationContext();
    }

    DiagnosticReport inspect() {
        DiagnosticReport report = new DiagnosticReport();
        PackageManager pm = context.getPackageManager();
        UserManager users = context.getSystemService(UserManager.class);

        CommandResult root = RootShell.run("id -u");
        report.root = root.ok() && "0".equals(root.output);
        report.systemUser = users != null && users.isSystemUser();
        report.singleUserProfile = users != null && users.getUserProfiles().size() == 1;
        if (report.root) {
            CommandResult userList = RootShell.run("cmd user list");
            if (userList.ok()) {
                long userCount = userList.output.lines()
                        .filter(line -> line.contains("UserInfo{"))
                        .count();
                report.singleUserProfile = report.singleUserProfile && userCount == 1
                        && userList.output.contains("UserInfo{0:");
            }
        }
        report.modelSupported = isSupportedS23Model(Build.MODEL);
        report.android16 = Build.VERSION.SDK_INT == 36;
        report.deviceGate = report.modelSupported && report.android16 && report.systemUser
                && report.singleUserProfile;
        report.exactEnvironmentGate = "SM-S918B".equalsIgnoreCase(Build.MODEL)
                && report.android16 && report.systemUser && report.singleUserProfile;

        report.launcherPresent = packageEnabled(pm, AppConstants.LAUNCHER_PACKAGE);
        report.quickSearchPresent = packageEnabled(pm, AppConstants.QUICK_SEARCH_PACKAGE);
        report.quickSearchIsHome = isQuickSearchHome();
        report.documentsUiReady = DocumentsUiTarget.isEligible(pm,
                AppConstants.QUICK_SEARCH_PACKAGE, new ComponentName(
                        AppConstants.DOCUMENTS_UI_PACKAGE,
                        AppConstants.DOCUMENTS_UI_FILES_ACTIVITY));
        report.contextualProviderPackage = contextualProvider(pm);
        report.googleProvider = AppConstants.GOOGLE_PACKAGE.equals(report.contextualProviderPackage);

        if (report.quickSearchPresent) {
            inspectQuickSearch(pm, report);
        }

        if (report.root) {
            CommandResult ksuVersion = RootShell.run(AppConstants.KSU_CLI + " -V");
            CommandResult ksuModules = RootShell.run(AppConstants.KSU_CLI + " module list");
            report.kernelSuReady = ksuVersion.ok() && ksuModules.ok();
            // Some KernelSU compatibility layers expose a magisk shim. KernelSU itself is
            // authoritative here; only a Magisk-only environment is rejected.
            report.magiskPresent = !report.kernelSuReady
                    && RootShell.run("command -v magisk >/dev/null 2>&1").ok();

            CommandResult metamodule = RootShell.run(
                    "test -L /data/adb/metamodule && test \"$(readlink -f "
                            + "/data/adb/metamodule)\" = \"/data/adb/modules/magic_mount_rs\"");
            report.magicMountReady = report.kernelSuReady && metamodule.ok()
                    && moduleActive(ksuModules.output, "magic_mount_rs", true);

            CommandResult vector = RootShell.run(AppConstants.VECTOR_CLI + " --json status");
            report.vectorStatus = vector.output;
            report.vectorReady = vector.ok() && vector.output.contains("\"API Version\": 102")
                    && vector.output.contains("\"success\": true");

            report.contextualFeature = RootShell.run("pm has-feature "
                    + AppConstants.CONTEXTUAL_FEATURE).output.contains("true");
            report.contextualService = RootShell.run(
                    "service list | grep -F 'contextual_search:' >/dev/null").ok();

            report.darkResource = resourceExists(AppConstants.DARK_RESOURCE);
            report.lightResource = resourceExists(AppConstants.LIGHT_RESOURCE);
            report.darkTransparent = resourceTransparent(AppConstants.DARK_RESOURCE);
            report.lightTransparent = resourceTransparent(AppConstants.LIGHT_RESOURCE);
            boolean overviewFiles = RootShell.run("test ! -e "
                    + AppConstants.OVERVIEW_MODULE_DIR + "/remove && { { test -f "
                    + AppConstants.OVERVIEW_MODULE_DIR + "/"
                    + AppConstants.OVERVIEW_OWNER_MARKER + " && test -f "
                    + AppConstants.OVERVIEW_MODULE_DIR + AppConstants.OVERVIEW_BOOT_SCRIPT
                    + "; } || { test -f " + AppConstants.OVERVIEW_MODULE_UPDATE_DIR + "/"
                    + AppConstants.OVERVIEW_OWNER_MARKER + " && test -f "
                    + AppConstants.OVERVIEW_MODULE_UPDATE_DIR
                    + AppConstants.OVERVIEW_BOOT_SCRIPT + "; }; }").ok();
            report.overviewModuleInstalled = overviewFiles && ksuModules.ok()
                    && moduleActive(ksuModules.output, AppConstants.OVERVIEW_MODULE_ID, false);
            report.overviewModuleRemovalPending = ownedModuleHasFlag(
                    AppConstants.OVERVIEW_MODULE_DIR,
                    AppConstants.OVERVIEW_MODULE_UPDATE_DIR,
                    AppConstants.OVERVIEW_OWNER_MARKER, "remove");
            boolean circleFiles = RootShell.run("test ! -e "
                    + AppConstants.CIRCLE_MODULE_DIR + "/remove && { { test -f "
                    + AppConstants.CIRCLE_MODULE_DIR + "/" + AppConstants.CIRCLE_OWNER_MARKER
                    + " && test -f " + AppConstants.CIRCLE_MODULE_DIR
                    + AppConstants.CIRCLE_XML_RELATIVE + "; } || { test -f "
                    + AppConstants.CIRCLE_MODULE_UPDATE_DIR + "/"
                    + AppConstants.CIRCLE_OWNER_MARKER + " && test -f "
                    + AppConstants.CIRCLE_MODULE_UPDATE_DIR
                    + AppConstants.CIRCLE_XML_RELATIVE + "; }; }").ok();
            report.circleModuleInstalled = circleFiles && ksuModules.ok()
                    && moduleActive(ksuModules.output, AppConstants.CIRCLE_MODULE_ID, false);
            report.circleModuleRemovalPending = ownedModuleHasFlag(
                    AppConstants.CIRCLE_MODULE_DIR,
                    AppConstants.CIRCLE_MODULE_UPDATE_DIR,
                    AppConstants.CIRCLE_OWNER_MARKER, "remove");

            report.searchAllEntrypointsEnabled = settingEnabled(
                    "secure", "search_all_entrypoints_enabled");
            report.navbarLongPressEnabled = settingEnabled(
                    "system", "navbar_long_press_gesture");

            CommandResult modules = RootShell.run(AppConstants.VECTOR_CLI + " modules ls");
            report.vectorModuleEnabled = modules.ok()
                    && modules.output.lines().anyMatch(line -> line.contains(AppConstants.APP_PACKAGE)
                    && line.toLowerCase(Locale.ROOT).contains("enabled"));
            CommandResult scope = RootShell.run(AppConstants.VECTOR_CLI + " scope ls "
                    + AppConstants.APP_PACKAGE);
            report.vectorScopeReady = scope.ok()
                    && scope.output.contains(AppConstants.QUICK_SEARCH_PACKAGE);
        }

        report.transparencySupported = supportsTransparency(report);
        report.backGuardSupported = supportsBackGuard(report);
        report.circleSupported = supportsCircle(report);
        report.debloatSupported = supportsDebloat(report);
        // Recovery deliberately survives ROM/profile compatibility changes. New mutations do not.
        report.debloatRestoreSupported = report.root && report.systemUser;
        report.leAudio = new LeAudioManager(context).inspect(report);

        if (!report.deviceGate) {
            report.notes.add("unsupported_device");
        }
        if (report.magiskPresent) {
            report.notes.add("magisk_blocked");
        }
        if (!report.quickSearchCompatible && report.quickSearchPresent) {
            report.notes.add("quicksearch_mismatch");
        }
        if (report.contextualFeature) {
            report.notes.add("contextual_already_present");
        }
        return report;
    }

    static boolean isSupportedS23Model(String model) {
        return model != null && SUPPORTED_MODEL.matcher(model.trim()).matches();
    }

    static boolean supportsTransparency(DiagnosticReport report) {
        return report.root && report.exactEnvironmentGate && report.kernelSuReady
                && !report.magiskPresent && report.magicMountReady && report.launcherPresent
                && report.darkResource && report.lightResource;
    }

    static boolean supportsBackGuard(DiagnosticReport report) {
        return report.root && report.exactEnvironmentGate && report.kernelSuReady
                && !report.magiskPresent && report.quickSearchCompatible
                && report.quickSearchIsHome && report.documentsUiReady
                && report.vectorReady;
    }

    static boolean supportsCircle(DiagnosticReport report) {
        return report.root && report.deviceGate && report.kernelSuReady
                && !report.magiskPresent && report.launcherPresent && report.googleProvider
                && report.contextualService
                && (report.contextualFeature || report.magicMountReady);
    }

    static boolean supportsDebloat(DiagnosticReport report) {
        return report.root && report.deviceGate && report.kernelSuReady
                && !report.magiskPresent;
    }

    private boolean settingEnabled(String namespace, String key) {
        CommandResult result = RootShell.run("settings --user 0 get " + namespace + " " + key);
        return result.ok() && "1".equals(result.output.trim());
    }

    private boolean ownedModuleHasFlag(String moduleDir, String updateDir,
                                       String ownerMarker, String flag) {
        return RootShell.run("{ test -f " + moduleDir + "/" + ownerMarker
                + " && test -e " + moduleDir + "/" + flag + "; } || { test -f "
                + updateDir + "/" + ownerMarker + " && test -e " + updateDir + "/"
                + flag + "; }").ok();
    }

    private boolean packageEnabled(PackageManager pm, String packageName) {
        try {
            return pm.getApplicationInfo(packageName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String contextualProvider(PackageManager pm) {
        Intent intent = new Intent(AppConstants.CONTEXTUAL_ACTION);
        List<ResolveInfo> matches = pm.queryIntentActivities(intent,
                PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        for (ResolveInfo match : matches) {
            if (match.activityInfo != null
                    && AppConstants.GOOGLE_PACKAGE.equals(match.activityInfo.packageName)) {
                return match.activityInfo.packageName;
            }
        }
        if (!matches.isEmpty() && matches.get(0).activityInfo != null) {
            return matches.get(0).activityInfo.packageName;
        }
        return "";
    }

    private boolean isQuickSearchHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(home,
                PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
                && AppConstants.QUICK_SEARCH_PACKAGE.equals(resolved.activityInfo.packageName);
    }

    private void inspectQuickSearch(PackageManager pm, DiagnosticReport report) {
        try {
            PackageInfo info = pm.getPackageInfo(AppConstants.QUICK_SEARCH_PACKAGE,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            ApplicationInfo appInfo = info.applicationInfo;
            if (appInfo != null) {
                report.quickSearchHash = sha256File(appInfo.sourceDir);
            }
            if (info.signingInfo != null) {
                Signature[] signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
                if (signatures.length > 0) {
                    report.quickSearchCertificate = sha256(signatures[0].toByteArray());
                }
            }
            report.quickSearchCompatible = info.getLongVersionCode()
                    == AppConstants.QUICK_SEARCH_VERSION
                    && AppConstants.QUICK_SEARCH_APK_SHA256.equals(report.quickSearchHash)
                    && AppConstants.QUICK_SEARCH_CERT_SHA256.equals(report.quickSearchCertificate);
        } catch (Exception e) {
            report.notes.add("quicksearch_check_failed");
        }
    }

    private boolean resourceExists(String resourceName) {
        CommandResult result = RootShell.run("cmd overlay lookup --user 0 "
                + AppConstants.LAUNCHER_PACKAGE + " " + resourceName);
        return result.ok() && !result.output.isBlank()
                && !result.output.toLowerCase(Locale.ROOT).contains("error");
    }

    private boolean resourceTransparent(String resourceName) {
        CommandResult result = RootShell.run("cmd overlay lookup --user 0 "
                + AppConstants.LAUNCHER_PACKAGE + " " + resourceName);
        if (!result.ok()) {
            return false;
        }
        String value = result.output.toLowerCase(Locale.ROOT).replace("0x", "#");
        return "#0".equals(value) || "#00000000".equals(value)
                || "#0000000000000000".equals(value);
    }

    static String sha256File(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static boolean moduleActive(String json, String id, boolean requireMetamodule) {
        try {
            JSONArray modules = new JSONArray(json);
            for (int index = 0; index < modules.length(); index++) {
                JSONObject module = modules.getJSONObject(index);
                boolean enabled = Boolean.parseBoolean(String.valueOf(module.opt("enabled")));
                boolean remove = Boolean.parseBoolean(String.valueOf(module.opt("remove")));
                boolean update = Boolean.parseBoolean(String.valueOf(module.opt("update")));
                boolean metamodule = "1".equals(String.valueOf(module.opt("metamodule")));
                if (id.equals(module.optString("id")) && enabled && !remove
                        && (!requireMetamodule || (metamodule && !update))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
