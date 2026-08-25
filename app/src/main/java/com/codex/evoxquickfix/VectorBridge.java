package com.codex.evoxquickfix;

import java.util.List;

import io.github.libxposed.service.XposedService;

final class VectorBridge {
    private VectorBridge() {}

    static String serviceSummary() {
        XposedService service = EvoQuickFixApp.vectorService();
        if (service == null) {
            return "Vector service: سيُستخدم CLI الرسمي للتهيئة الأولى";
        }
        try {
            return "Vector service: " + service.getFrameworkName() + " "
                    + service.getFrameworkVersion() + " / API " + service.getApiVersion();
        } catch (Throwable t) {
            return "Vector service: غير متاح مؤقتًا";
        }
    }

    static boolean scopeHasQuickSearch() {
        XposedService service = EvoQuickFixApp.vectorService();
        if (service == null) {
            return false;
        }
        try {
            return service.getScope().contains(AppConstants.QUICK_SEARCH_PACKAGE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void removeQuickSearchScopeIfAvailable() {
        XposedService service = EvoQuickFixApp.vectorService();
        if (service == null) {
            return;
        }
        try {
            service.removeScope(List.of(AppConstants.QUICK_SEARCH_PACKAGE));
        } catch (Throwable ignored) {
        }
    }
}
