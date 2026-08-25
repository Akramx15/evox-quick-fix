package com.codex.evoxquickfix;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class DeviceDiagnostics {
    private final Context context;

    DeviceDiagnostics(Context context) {
        this.context = context.getApplicationContext();
    }

    DiagnosticReport inspect() {
        DiagnosticReport report = new DiagnosticReport();
        PackageManager pm = context.getPackageManager();

        CommandResult root = RootShell.run("id -u");
        report.root = root.ok() && "0".equals(root.output);
        android.os.UserManager users = context.getSystemService(android.os.UserManager.class);
        report.deviceGate = "SM-S918B".equalsIgnoreCase(Build.MODEL)
                && Build.VERSION.SDK_INT == 36 && users != null && users.isSystemUser();

        report.launcherPresent = packageEnabled(pm, AppConstants.LAUNCHER_PACKAGE);
        report.quickSearchPresent = packageEnabled(pm, AppConstants.QUICK_SEARCH_PACKAGE);
        report.googleProvider = hasContextualProvider(pm);
        report.quickSearchIsHome = isQuickSearchHome();

        if (report.quickSearchPresent) {
            inspectQuickSearch(pm, report);
        }

        if (report.root) {
            CommandResult vector = RootShell.run(AppConstants.VECTOR_CLI + " --json status");
            report.vectorStatus = vector.output;
            report.vectorReady = vector.ok() && vector.output.contains("\"API Version\": 102")
                    && vector.output.contains("\"success\": true");

            CommandResult ksuModules = RootShell.run(AppConstants.KSU_CLI + " module list");
            CommandResult metamodule = RootShell.run(
                    "test -L /data/adb/metamodule && test \"$(readlink -f "
                            + "/data/adb/metamodule)\" = \"/data/adb/modules/magic_mount_rs\"");
            report.magicMountReady = ksuModules.ok() && metamodule.ok()
                    && moduleActive(ksuModules.output, "magic_mount_rs", true);
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

            CommandResult modules = RootShell.run(AppConstants.VECTOR_CLI + " modules ls");
            report.vectorModuleEnabled = modules.ok()
                    && modules.output.lines().anyMatch(line -> line.contains(AppConstants.APP_PACKAGE)
                    && line.toLowerCase(Locale.ROOT).contains("enabled"));
            CommandResult scope = RootShell.run(AppConstants.VECTOR_CLI + " scope ls "
                    + AppConstants.APP_PACKAGE);
            report.vectorScopeReady = scope.ok() && scope.output.contains(AppConstants.QUICK_SEARCH_PACKAGE);
        }

        if (!report.deviceGate) {
            report.notes.add("زر التطبيق مقفول لأن الأداة مخصصة لهذا الجهاز والإصدار فقط.");
        }
        if (!report.quickSearchCompatible && report.quickSearchPresent) {
            report.notes.add("Back Guard لن يعمل لأن نسخة/توقيع Quick Search لا يطابقان النسخة المدروسة.");
        }
        if (report.contextualFeature) {
            report.notes.add("ميزة Circle to Search معلنة بالفعل؛ لا تحتاج تكرار التثبيت.");
        }
        return report;
    }

    private boolean packageEnabled(PackageManager pm, String packageName) {
        try {
            return pm.getApplicationInfo(packageName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean hasContextualProvider(PackageManager pm) {
        Intent intent = new Intent(AppConstants.CONTEXTUAL_ACTION).setPackage(AppConstants.GOOGLE_PACKAGE);
        List<ResolveInfo> matches = pm.queryIntentActivities(intent,
                PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        return !matches.isEmpty();
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
            report.quickSearchCompatible = info.getLongVersionCode() == AppConstants.QUICK_SEARCH_VERSION
                    && AppConstants.QUICK_SEARCH_APK_SHA256.equals(report.quickSearchHash)
                    && AppConstants.QUICK_SEARCH_CERT_SHA256.equals(report.quickSearchCertificate);
        } catch (Exception e) {
            report.notes.add("تعذر التحقق من Quick Search: " + e.getClass().getSimpleName());
        }
    }

    private boolean resourceExists(String resourceName) {
        CommandResult result = RootShell.run("cmd overlay lookup --user 0 "
                + AppConstants.LAUNCHER_PACKAGE + " " + resourceName);
        return result.ok() && !result.output.isBlank() && !result.output.toLowerCase(Locale.ROOT).contains("error");
    }

    private boolean resourceTransparent(String resourceName) {
        CommandResult result = RootShell.run("cmd overlay lookup --user 0 "
                + AppConstants.LAUNCHER_PACKAGE + " " + resourceName);
        if (!result.ok()) {
            return false;
        }
        String value = result.output.toLowerCase(Locale.ROOT).replace("0x", "#");
        return "#0".equals(value) || "#00000000".equals(value) || "#0000000000000000".equals(value);
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
