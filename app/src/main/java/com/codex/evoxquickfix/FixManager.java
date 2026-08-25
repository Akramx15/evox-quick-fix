package com.codex.evoxquickfix;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class FixManager {
    private static final ReentrantLock TRANSACTION_LOCK = new ReentrantLock();
    private final Context context;
    private final DeviceDiagnostics diagnostics;
    private final SnapshotStore snapshots;

    FixManager(Context context) {
        this.context = context.getApplicationContext();
        this.diagnostics = new DeviceDiagnostics(context);
        this.snapshots = new SnapshotStore(context);
    }

    DiagnosticReport inspect() {
        return diagnostics.inspect();
    }

    OperationResult applyTransparency() throws Exception {
        return runTransactional("الشفافية", this::applyTransparencyInternal);
    }

    OperationResult applyBackGuard() throws Exception {
        return runTransactional("Back Guard", this::applyBackGuardInternal);
    }

    OperationResult applyCircleToSearch() throws Exception {
        return runTransactional("Circle to Search", this::applyCircleToSearchInternal);
    }

    OperationResult applyAll() throws Exception {
        return runTransactional("تطبيق الكل", () -> {
            DiagnosticReport before = diagnostics.inspect();
            if (!before.baseSupported() || !before.quickSearchCompatible
                    || !before.quickSearchIsHome) {
                throw new IllegalStateException("الفحص الشامل لم ينجح؛ لم يتم تطبيق أي تغيير");
            }
            OperationResult transparency = applyTransparencyInternal();
            applyBackGuardInternal();
            OperationResult circle = applyCircleToSearchInternal();
            return transparency.status == FeatureStatus.REBOOT_REQUIRED
                    || circle.status == FeatureStatus.REBOOT_REQUIRED
                    ? OperationResult.reboot(
                    "اكتملت الإصلاحات الثلاثة. يلزم Restart لتثبيت الاستمرار systemless.")
                    : OperationResult.applied("اكتملت الإصلاحات الثلاثة وتم التحقق منها.");
        });
    }

    OperationResult restoreRomBehavior() throws Exception {
        acquireTransactionLock();
        try {
            DiagnosticReport report = diagnostics.inspect();
            requireDeviceAndRoot(report);
            requireOwnedCircleModuleOrAbsent();
            requireOwnedOverviewModuleOrAbsent();
            JSONObject target = snapshots.load();
            if (target == null) {
                throw new IllegalStateException("لا يوجد Snapshot صالح؛ لم يتم أي تغيير");
            }
            JSONObject before = snapshots.captureCurrent();
            try {
                disableOverlay(AppConstants.DARK_OVERLAY);
                disableOverlay(AppConstants.LIGHT_OVERLAY);
                setVectorQuickSearchScope(false);
                setVectorModuleEnabled(false);

                boolean circlePresent = circleModuleOwned();
                boolean overviewPresent = overviewModuleOwned();
                if (circlePresent) {
                    RootShell.run(AppConstants.KSU_CLI + " module uninstall "
                                    + AppConstants.CIRCLE_MODULE_ID)
                            .requireSuccess("تعليم وحدة Circle للإزالة");
                }
                if (overviewPresent) {
                    RootShell.run(AppConstants.KSU_CLI + " module uninstall "
                                    + AppConstants.OVERVIEW_MODULE_ID)
                            .requireSuccess("تعليم وحدة استمرار الشفافية للإزالة");
                }
                SnapshotStore.restoreSetting(target, "searchAllEntrypoints", "secure",
                        "search_all_entrypoints_enabled");
                SnapshotStore.restoreSetting(target, "navbarLongPress", "system",
                        "navbar_long_press_gesture");
                restartQuickSearchHome();
                verifyDisabledPackages(before);

                return circlePresent || overviewPresent
                        ? OperationResult.reboot(
                        "تم تعطيل الإصلاحات. يلزم Restart لإزالة وحدات systemless.")
                        : OperationResult.applied("تم استرجاع سلوك الروم.");
            } catch (Throwable failure) {
                throw transactionFailure(
                        "استرجاع وضع الروم", failure, rollbackToSnapshot(before));
            }
        } finally {
            TRANSACTION_LOCK.unlock();
        }
    }

    void rebootDevice() {
        acquireTransactionLock();
        try {
            RootShell.run("sync; reboot", 10L);
        } finally {
            TRANSACTION_LOCK.unlock();
        }
    }

    private OperationResult applyTransparencyInternal() throws Exception {
        DiagnosticReport report = diagnostics.inspect();
        requireDeviceAndRoot(report);
        if (!report.launcherPresent || !report.darkResource || !report.lightResource) {
            throw new IllegalStateException("موارد Launcher3 المطلوبة غير موجودة");
        }

        requireOwnedOverviewModuleOrAbsent();
        File bootScript = stageAsset("overview_module/boot-completed.sh",
                "overview-boot-completed.sh");
        String expectedScriptHash = DeviceDiagnostics.sha256File(
                bootScript.getAbsolutePath());
        boolean alreadyOwned = overviewModuleOwned();
        if (alreadyOwned) {
            String payload = installedOverviewPayloadPath();
            if (payload == null || !fileHashMatches(payload, expectedScriptHash)) {
                throw new IllegalStateException(
                        "وحدة الشفافية المملوكة موجودة لكن محتواها غير مطابق؛ استرجعها أولًا");
            }
            if (rootTest("test -e " + AppConstants.OVERVIEW_MODULE_DIR + "/remove",
                    "فحص علامة إزالة وحدة الشفافية")) {
                RootShell.run(AppConstants.KSU_CLI + " module restore "
                                + AppConstants.OVERVIEW_MODULE_ID)
                        .requireSuccess("إلغاء علامة إزالة وحدة الشفافية");
            }
            if (rootTest("test -e " + AppConstants.OVERVIEW_MODULE_DIR + "/disable",
                    "فحص علامة تعطيل وحدة الشفافية")) {
                RootShell.run(AppConstants.KSU_CLI + " module enable "
                                + AppConstants.OVERVIEW_MODULE_ID)
                        .requireSuccess("إعادة تفعيل وحدة الشفافية");
            }
        } else {
            File moduleZip = stageOverviewModuleZip();
            installOwnedModule(moduleZip, AppConstants.OVERVIEW_MODULE_ID,
                    AppConstants.OVERVIEW_MODULE_DIR,
                    AppConstants.OVERVIEW_MODULE_UPDATE_DIR,
                    AppConstants.OVERVIEW_OWNER_MARKER,
                    "تثبيت وحدة استمرار الشفافية عبر KernelSU");
            String payload = installedOverviewPayloadPath();
            if (payload == null || !fileHashMatches(payload, expectedScriptHash)) {
                throw new IllegalStateException("Hash سكربت استمرار الشفافية غير مطابق");
            }
            if (!overviewModuleOwned()) {
                throw new IllegalStateException(
                        "تعذر إثبات ملكية وحدة استمرار الشفافية بعد التثبيت");
            }
        }
        if (!kernelSuModuleActive(AppConstants.OVERVIEW_MODULE_ID)) {
            throw new IllegalStateException("KernelSU لم يؤكد تفعيل وحدة استمرار الشفافية");
        }

        ensureFabricatedOverlay(AppConstants.DARK_OVERLAY, AppConstants.DARK_OVERLAY_NAME,
                AppConstants.DARK_RESOURCE);
        ensureFabricatedOverlay(AppConstants.LIGHT_OVERLAY, AppConstants.LIGHT_OVERLAY_NAME,
                AppConstants.LIGHT_RESOURCE);
        enableOverlay(AppConstants.DARK_OVERLAY);
        enableOverlay(AppConstants.LIGHT_OVERLAY);

        DiagnosticReport verified = diagnostics.inspect();
        if (!verified.darkTransparent || !verified.lightTransparent) {
            throw new IllegalStateException("فشل التحقق من قيمة الشفافية");
        }
        boolean updatePending = rootTest("test -d "
                + AppConstants.OVERVIEW_MODULE_UPDATE_DIR,
                "فحص تحديث وحدة استمرار الشفافية");
        return updatePending
                ? OperationResult.reboot(
                "تم تفعيل الشفافية وتثبيت استمرارها systemless؛ يلزم Restart للتحقق.")
                : OperationResult.applied(
                "تم تفعيل شفافية Recent Apps للوضعين الداكن والفاتح.");
    }

    private OperationResult applyBackGuardInternal() {
        DiagnosticReport report = diagnostics.inspect();
        requireDeviceAndRoot(report);
        if (!report.quickSearchCompatible || !report.quickSearchIsHome) {
            throw new IllegalStateException(
                    "نسخة Quick Search أو دور HOME غير مطابقين؛ تم الإيقاف بأمان");
        }
        if (!report.vectorReady) {
            throw new IllegalStateException("Vector API 102 غير جاهز");
        }

        setVectorModuleEnabled(true);
        // add is intentionally non-destructive; set would overwrite every existing scope.
        setVectorQuickSearchScope(true);
        restartQuickSearchHome();

        DiagnosticReport verified = diagnostics.inspect();
        if (!verified.vectorModuleEnabled || !verified.vectorScopeReady) {
            throw new IllegalStateException("Vector لم يؤكد تفعيل الوحدة والنطاق");
        }
        return OperationResult.applied("تم تفعيل Back Guard على HomeActivity فقط عبر Vector.");
    }

    private OperationResult applyCircleToSearchInternal() throws Exception {
        DiagnosticReport report = diagnostics.inspect();
        requireDeviceAndRoot(report);
        if (!report.magicMountReady) {
            throw new IllegalStateException("Magic Mount-rs metamodule غير جاهز");
        }
        if (!report.googleProvider || !report.contextualService) {
            throw new IllegalStateException(
                    "Google provider أو خدمة contextual_search غير متاحين");
        }
        requireOwnedCircleModuleOrAbsent();

        File featureXml = stageAsset("circle_module/contextual_search_feature.xml",
                "contextual-search-feature.xml");
        String expectedXmlHash = DeviceDiagnostics.sha256File(featureXml.getAbsolutePath());
        boolean alreadyOwned = circleModuleOwned();
        if (alreadyOwned) {
            String payload = installedCirclePayloadPath();
            if (payload == null || !fileHashMatches(payload, expectedXmlHash)) {
                throw new IllegalStateException(
                        "وحدة Circle المملوكة موجودة لكن محتواها غير مطابق؛ استرجعها أولًا");
            }
            if (rootTest("test -e " + AppConstants.CIRCLE_MODULE_DIR + "/remove",
                    "فحص علامة إزالة Circle")) {
                RootShell.run(AppConstants.KSU_CLI + " module restore "
                                + AppConstants.CIRCLE_MODULE_ID)
                        .requireSuccess("إلغاء علامة إزالة وحدة Circle");
            }
            if (rootTest("test -e " + AppConstants.CIRCLE_MODULE_DIR + "/disable",
                    "فحص علامة تعطيل Circle")) {
                RootShell.run(AppConstants.KSU_CLI + " module enable "
                                + AppConstants.CIRCLE_MODULE_ID)
                        .requireSuccess("إعادة تفعيل وحدة Circle");
            }
        } else {
            File moduleZip = stageCircleModuleZip();
            installCircleModule(moduleZip);
            String payload = installedCirclePayloadPath();
            if (payload == null || !fileHashMatches(payload, expectedXmlHash)) {
                throw new IllegalStateException("Hash ملف ميزة Circle غير مطابق بعد التثبيت");
            }
        }

        if (!kernelSuModuleActive(AppConstants.CIRCLE_MODULE_ID)) {
            throw new IllegalStateException("KernelSU لم يؤكد أن وحدة Circle مفعلة");
        }
        RootShell.run("settings --user 0 put secure search_all_entrypoints_enabled 1")
                .requireSuccess("تفعيل search_all_entrypoints");
        RootShell.run("settings --user 0 put system navbar_long_press_gesture 1")
                .requireSuccess("تفعيل الضغط المطول لشريط الإيماءات");

        DiagnosticReport verified = diagnostics.inspect();
        if (!verified.circleModuleInstalled) {
            throw new IllegalStateException("لم يتم تسجيل الوحدة بنجاح");
        }
        if (verified.contextualFeature) {
            return OperationResult.applied("Circle to Search جاهز والميزة معلنة بالفعل.");
        }
        return OperationResult.reboot(
                "تم تثبيت ميزة Circle to Search systemless؛ يلزم Restart واحد.");
    }

    private OperationResult runTransactional(String label, TransactionBody operation)
            throws Exception {
        acquireTransactionLock();
        try {
            DiagnosticReport report = diagnostics.inspect();
            requireDeviceAndRoot(report);
            snapshots.ensureSnapshot();
            JSONObject before = snapshots.captureCurrent();
            try {
                OperationResult result = operation.run();
                verifyDisabledPackages(before);
                return result;
            } catch (Throwable failure) {
                throw transactionFailure(label, failure, rollbackToSnapshot(before));
            }
        } finally {
            TRANSACTION_LOCK.unlock();
        }
    }

    private static void acquireTransactionLock() {
        if (!TRANSACTION_LOCK.tryLock()) {
            throw new IllegalStateException("هناك عملية إصلاح أخرى قيد التنفيذ؛ انتظر اكتمالها");
        }
    }

    private IllegalStateException transactionFailure(String label, Throwable failure,
                                                     List<String> rollbackFailures) {
        String cause = failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
        if (rollbackFailures.isEmpty()) {
            return new IllegalStateException(label + " فشل وتم استرجاع حالة ما قبل العملية: "
                    + cause, failure);
        }
        return new IllegalStateException(label + " فشل، والاسترجاع غير مكتمل ("
                + String.join("؛ ", rollbackFailures) + "): " + cause, failure);
    }

    private List<String> rollbackToSnapshot(JSONObject snapshot) {
        List<String> failures = new ArrayList<>();
        rollbackStep(failures, "overlay الداكن", () -> setOverlay(AppConstants.DARK_OVERLAY,
                snapshot.optBoolean("darkOverlayEnabled", false)));
        rollbackStep(failures, "overlay الفاتح", () -> setOverlay(AppConstants.LIGHT_OVERLAY,
                snapshot.optBoolean("lightOverlayEnabled", false)));
        rollbackStep(failures, "وحدة استمرار الشفافية",
                () -> restoreOverviewState(snapshot));
        rollbackStep(failures, "نطاق Vector", () -> setVectorQuickSearchScope(
                snapshot.optBoolean("vectorScopeHadQuickSearch", false)));
        rollbackStep(failures, "وحدة Vector", () -> setVectorModuleEnabled(
                snapshot.optBoolean("vectorModuleEnabled", false)));
        rollbackStep(failures, "وحدة Circle", () -> restoreCircleState(snapshot));
        rollbackStep(failures, "إعداد البحث", () -> SnapshotStore.restoreSetting(snapshot,
                "searchAllEntrypoints", "secure", "search_all_entrypoints_enabled"));
        rollbackStep(failures, "إعداد شريط الإيماءات", () -> SnapshotStore.restoreSetting(snapshot,
                "navbarLongPress", "system", "navbar_long_press_gesture"));
        rollbackStep(failures, "Quick Search HOME", this::restartQuickSearchHome);
        rollbackStep(failures, "قائمة الحزم المعطلة", () -> verifyDisabledPackages(snapshot));
        return failures;
    }

    private void restoreOverviewState(JSONObject snapshot) {
        boolean existed = snapshot.optBoolean("overviewModuleExisted", false);
        if (!existed) {
            if (overviewModuleOwned()) {
                RootShell.run(AppConstants.KSU_CLI + " module uninstall "
                                + AppConstants.OVERVIEW_MODULE_ID)
                        .requireSuccess("إزالة وحدة الشفافية الجديدة");
                throw new IllegalStateException(
                        "وحدة الشفافية الجديدة عُلّمت للإزالة وتحتاج Restart لإكمال rollback");
            }
            return;
        }
        if (!overviewModuleOwned()) {
            throw new IllegalStateException("وحدة الشفافية الأصلية لم تعد موجودة");
        }
        if (snapshot.optBoolean("overviewRemoveMarked", false)) {
            RootShell.run(AppConstants.KSU_CLI + " module uninstall "
                            + AppConstants.OVERVIEW_MODULE_ID)
                    .requireSuccess("استرجاع علامة إزالة وحدة الشفافية");
        } else {
            RootShell.run(AppConstants.KSU_CLI + " module restore "
                            + AppConstants.OVERVIEW_MODULE_ID)
                    .requireSuccess("إلغاء علامة إزالة وحدة الشفافية");
        }
        String enabledVerb = snapshot.optBoolean("overviewDisableMarked", false)
                ? "disable" : "enable";
        RootShell.run(AppConstants.KSU_CLI + " module " + enabledVerb + " "
                        + AppConstants.OVERVIEW_MODULE_ID)
                .requireSuccess("استرجاع حالة وحدة الشفافية");
    }

    private void restoreCircleState(JSONObject snapshot) {
        boolean existed = snapshot.optBoolean("circleModuleExisted", false);
        if (!existed) {
            if (circleModuleOwned()) {
                RootShell.run(AppConstants.KSU_CLI + " module uninstall "
                                + AppConstants.CIRCLE_MODULE_ID)
                        .requireSuccess("إزالة وحدة Circle الجديدة");
                throw new IllegalStateException(
                        "وحدة Circle الجديدة عُلّمت للإزالة وتحتاج Restart لإكمال rollback");
            }
            return;
        }
        if (!circleModuleOwned()) {
            throw new IllegalStateException("وحدة Circle الأصلية لم تعد موجودة");
        }
        if (snapshot.optBoolean("circleRemoveMarked", false)) {
            RootShell.run(AppConstants.KSU_CLI + " module uninstall "
                            + AppConstants.CIRCLE_MODULE_ID)
                    .requireSuccess("استرجاع علامة إزالة Circle");
        } else {
            RootShell.run(AppConstants.KSU_CLI + " module restore "
                            + AppConstants.CIRCLE_MODULE_ID)
                    .requireSuccess("إلغاء علامة إزالة Circle");
        }
        String enabledVerb = snapshot.optBoolean("circleDisableMarked", false)
                ? "disable" : "enable";
        RootShell.run(AppConstants.KSU_CLI + " module " + enabledVerb + " "
                        + AppConstants.CIRCLE_MODULE_ID)
                .requireSuccess("استرجاع حالة تفعيل Circle");
    }

    private void rollbackStep(List<String> failures, String name, RollbackStep action) {
        try {
            action.run();
        } catch (Throwable failure) {
            String message = failure.getMessage();
            failures.add(name + (message == null ? "" : ": " + message));
        }
    }

    private void verifyDisabledPackages(JSONObject snapshot) throws Exception {
        String before = snapshot.optString("disabledPackagesSha256", "");
        String after = snapshots.disabledPackagesDigest();
        if (before.isBlank() || !before.equals(after)) {
            throw new IllegalStateException("تغيرت قائمة الحزم المعطلة بشكل غير متوقع");
        }
    }

    private void ensureFabricatedOverlay(String identifier, String name, String resource) {
        CommandResult list = RootShell.run("cmd overlay list --user 0 "
                + AppConstants.LAUNCHER_PACKAGE);
        list.requireSuccess("قراءة overlays قبل الإنشاء");
        if (list.output.contains(identifier)) {
            return;
        }
        RootShell.run("cmd overlay fabricate --target "
                        + AppConstants.LAUNCHER_PACKAGE + " --name " + name + " "
                        + resource + " 0x1c 0x00000000")
                .requireSuccess("إنشاء " + name);
    }

    private void enableOverlay(String identifier) {
        RootShell.run("cmd overlay enable --user 0 " + identifier)
                .requireSuccess("تفعيل " + identifier);
    }

    private void disableOverlay(String identifier) {
        CommandResult exists = RootShell.run("cmd overlay list --user 0 "
                + AppConstants.LAUNCHER_PACKAGE);
        exists.requireSuccess("قراءة overlays قبل التعطيل");
        if (!exists.output.contains(identifier)) {
            return;
        }
        RootShell.run("cmd overlay disable --user 0 " + identifier)
                .requireSuccess("تعطيل " + identifier);
    }

    private void setOverlay(String identifier, boolean enabled) {
        if (enabled) {
            enableOverlay(identifier);
        } else {
            disableOverlay(identifier);
        }
    }

    private File stageAsset(String assetPath, String fileName) throws Exception {
        File staging = new File(context.getFilesDir(), "module-staging");
        if (!staging.isDirectory() && !staging.mkdirs()) {
            throw new IllegalStateException("تعذر إنشاء مجلد staging");
        }
        File target = new File(staging, fileName);
        try (InputStream input = context.getAssets().open(assetPath);
             FileOutputStream output = new FileOutputStream(target, false)) {
            input.transferTo(output);
            output.getFD().sync();
        }
        return target;
    }

    private File stageCircleModuleZip() throws Exception {
        File staging = new File(context.getFilesDir(), "module-staging");
        if (!staging.isDirectory() && !staging.mkdirs()) {
            throw new IllegalStateException("تعذر إنشاء مجلد staging");
        }
        File target = new File(staging, "evox-contextual-search-fix.zip");
        try (FileOutputStream raw = new FileOutputStream(target, false);
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            addAssetToZip(zip, "circle_module/module.prop", "module.prop");
            addAssetToZip(zip, "circle_module/contextual_search_feature.xml",
                    "system/etc/permissions/evox_contextual_search.xml");
            addAssetToZip(zip, "circle_module/owner.marker",
                    AppConstants.CIRCLE_OWNER_MARKER);
            zip.finish();
            zip.flush();
            raw.getFD().sync();
        }
        return target;
    }

    private File stageOverviewModuleZip() throws Exception {
        File staging = new File(context.getFilesDir(), "module-staging");
        if (!staging.isDirectory() && !staging.mkdirs()) {
            throw new IllegalStateException("تعذر إنشاء مجلد staging");
        }
        File target = new File(staging, "evox-overview-transparency.zip");
        try (FileOutputStream raw = new FileOutputStream(target, false);
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            addAssetToZip(zip, "overview_module/module.prop", "module.prop");
            addAssetToZip(zip, "overview_module/boot-completed.sh", "boot-completed.sh");
            addAssetToZip(zip, "overview_module/owner.marker",
                    AppConstants.OVERVIEW_OWNER_MARKER);
            addAssetToZip(zip, "overview_module/skip_mount", "skip_mount");
            zip.finish();
            zip.flush();
            raw.getFD().sync();
        }
        return target;
    }

    private void addAssetToZip(ZipOutputStream zip, String assetPath, String entryName)
            throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        try (InputStream input = context.getAssets().open(assetPath)) {
            input.transferTo(zip);
        }
        zip.closeEntry();
    }

    private void installCircleModule(File moduleZip) {
        installOwnedModule(moduleZip, AppConstants.CIRCLE_MODULE_ID,
                AppConstants.CIRCLE_MODULE_DIR, AppConstants.CIRCLE_MODULE_UPDATE_DIR,
                AppConstants.CIRCLE_OWNER_MARKER,
                "تثبيت وحدة Circle عبر KernelSU");
    }

    private void installOwnedModule(File moduleZip, String moduleId, String moduleDir,
                                    String updateDir, String ownerMarker,
                                    String description) {
        String rootZip = AppConstants.ROOT_STATE_DIR + "/module-install-" + moduleId + "-"
                + Long.toUnsignedString(System.nanoTime()) + ".zip";
        String command = "if test -L " + AppConstants.ROOT_STATE_DIR
                + "; then exit 45; fi; mkdir -p " + AppConstants.ROOT_STATE_DIR
                + " && chown 0:0 " + AppConstants.ROOT_STATE_DIR
                + " && chmod 700 " + AppConstants.ROOT_STATE_DIR
                + " && cp " + ShellEscaper.quote(moduleZip.getAbsolutePath()) + " "
                + ShellEscaper.quote(rootZip)
                + " && chown 0:0 " + ShellEscaper.quote(rootZip)
                + " && chmod 600 " + ShellEscaper.quote(rootZip)
                + " && " + AppConstants.KSU_CLI + " module install "
                + ShellEscaper.quote(rootZip)
                + "; status=$?; if test $status -eq 0; then cp "
                + updateDir + "/" + ownerMarker + " "
                + moduleDir + "/" + ownerMarker + " && chown 0:0 "
                + moduleDir + "/" + ownerMarker + " && chmod 644 "
                + moduleDir + "/" + ownerMarker + " || status=$?; fi; rm -f "
                + ShellEscaper.quote(rootZip)
                + "; exit $status";
        RootShell.run(command, 60L).requireSuccess(description);
    }

    private void requireOwnedCircleModuleOrAbsent() {
        if (rootTest("test -L " + AppConstants.CIRCLE_MODULE_DIR
                + " -o -L " + AppConstants.CIRCLE_MODULE_UPDATE_DIR,
                "فحص Symlink لوحدة Circle")) {
            throw new IllegalStateException("مسار وحدة Circle عبارة عن Symlink غير آمن");
        }
        boolean currentExists = rootTest("test -d " + AppConstants.CIRCLE_MODULE_DIR,
                "فحص وحدة Circle الحالية");
        boolean updateExists = rootTest("test -d " + AppConstants.CIRCLE_MODULE_UPDATE_DIR,
                "فحص تحديث وحدة Circle");
        if (!currentExists && !updateExists) {
            return;
        }
        boolean currentOwned = currentExists
                && ownedCircleModuleAt(AppConstants.CIRCLE_MODULE_DIR);
        boolean updateOwned = updateExists
                && ownedCircleModuleAt(AppConstants.CIRCLE_MODULE_UPDATE_DIR);
        if ((currentExists && !currentOwned) || (updateExists && !updateOwned)) {
            throw new IllegalStateException(
                    "يوجد Module ID مطابق لا تملكه الأداة؛ لم يتم لمسه");
        }
    }

    private boolean circleModuleOwned() {
        boolean currentExists = rootTest("test -d " + AppConstants.CIRCLE_MODULE_DIR,
                "فحص وحدة Circle الحالية");
        boolean updateExists = rootTest("test -d " + AppConstants.CIRCLE_MODULE_UPDATE_DIR,
                "فحص تحديث وحدة Circle");
        return (currentExists && ownedCircleModuleAt(AppConstants.CIRCLE_MODULE_DIR))
                || (updateExists && ownedCircleModuleAt(AppConstants.CIRCLE_MODULE_UPDATE_DIR));
    }

    private String installedCirclePayloadPath() {
        String update = AppConstants.CIRCLE_MODULE_UPDATE_DIR
                + AppConstants.CIRCLE_XML_RELATIVE;
        if (rootTest("test -f " + update, "فحص XML تحديث Circle")) {
            return update;
        }
        String current = AppConstants.CIRCLE_MODULE_DIR + AppConstants.CIRCLE_XML_RELATIVE;
        return rootTest("test -f " + current, "فحص XML الحالي لـCircle")
                ? current : null;
    }

    private boolean ownedCircleModuleAt(String directory) {
        return ownedModuleAt(directory, AppConstants.CIRCLE_OWNER_MARKER,
                AppConstants.CIRCLE_OWNER_SHA256, AppConstants.CIRCLE_MODULE_PROP_SHA256,
                "التحقق من ملكية وحدة Circle");
    }

    private void requireOwnedOverviewModuleOrAbsent() {
        if (rootTest("test -L " + AppConstants.OVERVIEW_MODULE_DIR
                + " -o -L " + AppConstants.OVERVIEW_MODULE_UPDATE_DIR,
                "فحص Symlink لوحدة الشفافية")) {
            throw new IllegalStateException("مسار وحدة الشفافية عبارة عن Symlink غير آمن");
        }
        boolean currentExists = rootTest("test -d " + AppConstants.OVERVIEW_MODULE_DIR,
                "فحص وحدة الشفافية الحالية");
        boolean updateExists = rootTest("test -d "
                + AppConstants.OVERVIEW_MODULE_UPDATE_DIR,
                "فحص تحديث وحدة الشفافية");
        if (!currentExists && !updateExists) {
            return;
        }
        boolean currentOwned = currentExists
                && ownedOverviewModuleAt(AppConstants.OVERVIEW_MODULE_DIR);
        boolean updateOwned = updateExists
                && ownedOverviewModuleAt(AppConstants.OVERVIEW_MODULE_UPDATE_DIR);
        if ((currentExists && !currentOwned) || (updateExists && !updateOwned)) {
            throw new IllegalStateException(
                    "يوجد Module ID مطابق لوحدة الشفافية لا تملكه الأداة؛ لم يتم لمسه");
        }
    }

    private boolean overviewModuleOwned() {
        boolean currentExists = rootTest("test -d " + AppConstants.OVERVIEW_MODULE_DIR,
                "فحص وحدة الشفافية الحالية");
        boolean updateExists = rootTest("test -d "
                + AppConstants.OVERVIEW_MODULE_UPDATE_DIR,
                "فحص تحديث وحدة الشفافية");
        return (currentExists && ownedOverviewModuleAt(AppConstants.OVERVIEW_MODULE_DIR))
                || (updateExists
                && ownedOverviewModuleAt(AppConstants.OVERVIEW_MODULE_UPDATE_DIR));
    }

    private String installedOverviewPayloadPath() {
        String update = AppConstants.OVERVIEW_MODULE_UPDATE_DIR
                + AppConstants.OVERVIEW_BOOT_SCRIPT;
        if (rootTest("test -f " + update, "فحص سكربت تحديث الشفافية")) {
            return update;
        }
        String current = AppConstants.OVERVIEW_MODULE_DIR
                + AppConstants.OVERVIEW_BOOT_SCRIPT;
        return rootTest("test -f " + current, "فحص سكربت الشفافية الحالي")
                ? current : null;
    }

    private boolean ownedOverviewModuleAt(String directory) {
        return ownedModuleAt(directory, AppConstants.OVERVIEW_OWNER_MARKER,
                AppConstants.OVERVIEW_OWNER_SHA256,
                AppConstants.OVERVIEW_MODULE_PROP_SHA256,
                "التحقق من ملكية وحدة الشفافية");
    }

    private boolean ownedModuleAt(String directory, String ownerMarker,
                                  String ownerHash, String modulePropHash,
                                  String description) {
        CommandResult hashes = RootShell.run("sha256sum " + directory + "/"
                + ownerMarker + " " + directory + "/module.prop");
        if (!hashes.timedOut && hashes.exitCode == 1) {
            return false;
        }
        hashes.requireSuccess(description);
        return hashes.output.contains(ownerHash) && hashes.output.contains(modulePropHash);
    }

    private static boolean rootTest(String command, String description) {
        CommandResult result = RootShell.run(command);
        if (!result.timedOut && result.exitCode == 1) {
            return false;
        }
        result.requireSuccess(description);
        return true;
    }

    private boolean fileHashMatches(String path, String expectedHash) {
        CommandResult hash = RootShell.run("sha256sum " + ShellEscaper.quote(path));
        return hash.ok() && hash.output.startsWith(expectedHash + " ");
    }

    private boolean kernelSuModuleActive(String moduleId) {
        CommandResult modules = RootShell.run(AppConstants.KSU_CLI + " module list");
        if (!modules.ok()) {
            return false;
        }
        try {
            JSONArray list = new JSONArray(modules.output);
            for (int index = 0; index < list.length(); index++) {
                JSONObject module = list.getJSONObject(index);
                boolean enabled = Boolean.parseBoolean(String.valueOf(module.opt("enabled")));
                boolean remove = Boolean.parseBoolean(String.valueOf(module.opt("remove")));
                if (moduleId.equals(module.optString("id"))
                        && enabled && !remove) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void setVectorModuleEnabled(boolean enabled) {
        String verb = enabled ? "enable" : "disable";
        runVectorJson("modules " + verb + " " + AppConstants.APP_PACKAGE,
                "تغيير حالة وحدة Vector");
        if (vectorModuleEnabledStrict() != enabled) {
            throw new IllegalStateException("Vector لم يثبت حالة الوحدة المطلوبة");
        }
    }

    private boolean vectorModuleEnabledStrict() {
        JSONObject result = runVectorJson("modules ls", "قراءة وحدات Vector");
        JSONArray data = result.optJSONArray("data");
        if (data == null) {
            throw new IllegalStateException("Vector أعاد قائمة وحدات غير صالحة");
        }
        for (int index = 0; index < data.length(); index++) {
            JSONObject module = data.optJSONObject(index);
            if (module != null
                    && AppConstants.APP_PACKAGE.equals(module.optString("PACKAGE"))) {
                return "enabled".equalsIgnoreCase(module.optString("STATUS"));
            }
        }
        return false;
    }

    private void setVectorQuickSearchScope(boolean present) {
        String verb = present ? "add" : "rm";
        runVectorJson("scope " + verb + " " + AppConstants.APP_PACKAGE + " "
                        + AppConstants.QUICK_SEARCH_PACKAGE + "/0",
                "تغيير نطاق Quick Search في Vector");
        if (vectorScopeContainsQuickSearchStrict() != present) {
            throw new IllegalStateException("Vector لم يثبت حالة نطاق Quick Search المطلوبة");
        }
    }

    private boolean vectorScopeContainsQuickSearchStrict() {
        JSONObject result = runVectorJson("scope ls " + AppConstants.APP_PACKAGE,
                "قراءة نطاق Vector");
        JSONArray data = result.optJSONArray("data");
        if (data == null) {
            throw new IllegalStateException("Vector أعاد قائمة نطاق غير صالحة");
        }
        for (int index = 0; index < data.length(); index++) {
            Object entry = data.opt(index);
            if (entry != null && entry.toString().contains(AppConstants.QUICK_SEARCH_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    private JSONObject runVectorJson(String arguments, String description) {
        CommandResult command = RootShell.run(AppConstants.VECTOR_CLI + " --json " + arguments);
        command.requireSuccess(description);
        try {
            JSONObject result = new JSONObject(command.output);
            if (!result.optBoolean("success", false)) {
                throw new IllegalStateException(description + ": Vector success=false");
            }
            return result;
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (Exception parseFailure) {
            throw new IllegalStateException(description + ": JSON غير صالح", parseFailure);
        }
    }

    private void restartQuickSearchHome() {
        RootShell.run("am force-stop " + AppConstants.QUICK_SEARCH_PACKAGE)
                .requireSuccess("إيقاف Quick Search لإعادة تحميله");
        RootShell.run("am start --user 0 -a android.intent.action.MAIN "
                        + "-c android.intent.category.HOME >/dev/null")
                .requireSuccess("تشغيل شاشة HOME");
    }

    private static void requireDeviceAndRoot(DiagnosticReport report) {
        if (!report.root) {
            throw new IllegalStateException("صلاحية Root غير متاحة");
        }
        if (!report.deviceGate) {
            throw new IllegalStateException("الأداة مقفلة على SM-S918B / Android 16 / User 0");
        }
    }

    @FunctionalInterface
    private interface TransactionBody {
        OperationResult run() throws Exception;
    }

    @FunctionalInterface
    private interface RollbackStep {
        void run() throws Exception;
    }
}
