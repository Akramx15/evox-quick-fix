package com.codex.evoxquickfix;

import android.app.Activity;
import android.app.role.RoleManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedModule;

public final class BackGuardModule extends XposedModule {
    private static final String TAG = "EvoXQuickFix";
    private boolean installed;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Module loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (installed || !param.isFirstPackage()
                || !AppConstants.QUICK_SEARCH_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            String source = param.getApplicationInfo().sourceDir;
            String hash = DeviceDiagnostics.sha256File(source);
            if (!AppConstants.QUICK_SEARCH_APK_SHA256.equals(hash)) {
                log(Log.WARN, TAG, "Quick Search APK hash mismatch; Back Guard remains inactive");
                return;
            }

            Method moveTaskToBack = Activity.class.getDeclaredMethod("moveTaskToBack", boolean.class);
            hook(moveTaskToBack)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
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
                        log(Log.INFO, TAG, "Blocked moveTaskToBack for Quick Search HomeActivity");
                        // The request was deliberately not performed, so report false.
                        return Boolean.FALSE;
                    });
            installed = true;
            log(Log.INFO, TAG, "Back Guard installed for Quick Search HomeActivity");
        } catch (Throwable failure) {
            log(Log.ERROR, TAG, "Back Guard hook failed", failure);
        }
    }
}
