package com.codex.evoxquickfix;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

final class DocumentsUiTarget {
    private DocumentsUiTarget() {}

    static boolean isEligible(PackageManager pm, String callerPackage,
                              ComponentName component) {
        try {
            ActivityInfo info = pm.getActivityInfo(component,
                    PackageManager.ComponentInfoFlags.of(0));
            ApplicationInfo app = info.applicationInfo;
            if (app == null || !info.enabled || !app.enabled || !info.exported
                    || (app.flags & ApplicationInfo.FLAG_INSTALLED) == 0
                    || (app.flags & ApplicationInfo.FLAG_SUSPENDED) != 0
                    || pm.isPackageSuspended(component.getPackageName())) {
                return false;
            }
            String permission = info.permission;
            return permission == null || permission.isEmpty()
                    || pm.checkPermission(permission, callerPackage)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
}
