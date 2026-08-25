package com.codex.evoxquickfix;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface.HookHandle;

public final class BackGuardModule extends XposedModule {
    private static final String TAG = "EvoXQuickFix";
    private final Object hookLock = new Object();
    private volatile HookSet installedHooks;

    private static final class HookSet {
        volatile boolean active;
        HookHandle backGuard;
        HookHandle folderRouter;
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Module loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        HookSet current = installedHooks;
        if ((current != null && current.active) || !param.isFirstPackage()
                || !AppConstants.QUICK_SEARCH_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            if (!QuickSearchRuntimePolicy.isSupported(
                    Build.MODEL, Build.VERSION.SDK_INT, param.getApplicationInfo().uid)) {
                log(Log.WARN, TAG,
                        "Quick Search environment mismatch; Quick Search fixes remain inactive");
                return;
            }
            String source = param.getApplicationInfo().sourceDir;
            String hash = DeviceDiagnostics.sha256File(source);
            if (!AppConstants.QUICK_SEARCH_APK_SHA256.equals(hash)) {
                log(Log.WARN, TAG,
                        "Quick Search APK hash mismatch; Quick Search fixes remain inactive");
                return;
            }
            installQuickSearchHooks();
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "Quick Search hook setup failed", failure);
        }
    }

    private void installQuickSearchHooks() throws Throwable {
        Method moveTaskToBack = Activity.class.getDeclaredMethod("moveTaskToBack", boolean.class);
        Method startActivity = ContextWrapper.class.getDeclaredMethod(
                "startActivity", Intent.class);

        synchronized (hookLock) {
            HookSet current = installedHooks;
            if (current != null && current.active) {
                return;
            }

            HookSet candidate = new HookSet();
            try {
                candidate.backGuard = hook(moveTaskToBack)
                        .setPriority(PRIORITY_HIGHEST)
                        .intercept(chain -> {
                            if (!candidate.active) {
                                return chain.proceed();
                            }
                            Object receiver = chain.getThisObject();
                            List<?> args = chain.getArgs();
                            if (!(receiver instanceof Activity activity)) {
                                return chain.proceed();
                            }
                            RoleManager roles = activity.getSystemService(RoleManager.class);
                            boolean holdsHome = roles != null
                                    && roles.isRoleHeld(RoleManager.ROLE_HOME);
                            if (!BackGuardPolicy.shouldBlock(
                                    activity.getClass().getName(), args, holdsHome)) {
                                return chain.proceed();
                            }
                            log(Log.INFO, TAG,
                                    "Blocked moveTaskToBack for Quick Search HomeActivity");
                            // The request was deliberately not performed, so report false.
                            return Boolean.FALSE;
                        });
                candidate.folderRouter = hook(startActivity)
                        .setPriority(PRIORITY_HIGHEST)
                        .intercept(chain -> {
                            if (!candidate.active) {
                                return chain.proceed();
                            }
                            Object receiver = chain.getThisObject();
                            Object value = chain.getArg(0);
                            if (!(receiver instanceof Context context)
                                    || !(value instanceof Intent original)) {
                                return chain.proceed();
                            }
                            Uri data = original.getData();
                            if (data == null || !QuickSearchFolderPolicy.shouldRoute(
                                    original.getAction(), original.getType(), data.getScheme(),
                                    data.getAuthority(), data.getPath(), original.getFlags(),
                                    original.getComponent() == null
                                            && original.getPackage() == null)) {
                                return chain.proceed();
                            }

                            ComponentName files = new ComponentName(
                                    AppConstants.DOCUMENTS_UI_PACKAGE,
                                    AppConstants.DOCUMENTS_UI_FILES_ACTIVITY);
                            if (!DocumentsUiTarget.isEligible(context.getPackageManager(),
                                    AppConstants.QUICK_SEARCH_PACKAGE, files)) {
                                log(Log.WARN, TAG,
                                        "DocumentsUI FilesActivity is unavailable; keeping ROM behavior");
                                return chain.proceed();
                            }

                            Intent routed = new Intent(original);
                            routed.setPackage(null);
                            routed.setComponent(files);
                            routed.setClipData(null);
                            routed.removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                            log(Log.INFO, TAG,
                                    "Routed Quick Search file folder to DocumentsUI without URI re-grant");
                            return chain.proceed(new Object[]{routed});
                        });
                installedHooks = candidate;
                candidate.active = true;
                log(Log.INFO, TAG, "Back Guard installed for Quick Search HomeActivity");
                log(Log.INFO, TAG, "Quick Search file-folder router installed");
            } catch (Throwable failure) {
                candidate.active = false;
                unhookSuppress(candidate.folderRouter, failure);
                unhookSuppress(candidate.backGuard, failure);
                if (installedHooks == candidate) {
                    installedHooks = null;
                }
                throw failure;
            }
        }
    }

    private void unhookSuppress(HookHandle handle, Throwable primary) {
        if (handle != null) {
            try {
                handle.unhook();
            } catch (Throwable rollbackFailure) {
                primary.addSuppressed(rollbackFailure);
                log(Log.ERROR, TAG,
                        "Quick Search hook rollback failed; the hook remains inactive",
                        rollbackFailure);
            }
        }
    }
}
