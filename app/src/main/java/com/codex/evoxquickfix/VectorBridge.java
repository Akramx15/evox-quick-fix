package com.codex.evoxquickfix;

import android.content.Context;

import java.util.List;

import io.github.libxposed.service.XposedService;

final class VectorBridge {
    private VectorBridge() {}

    static String serviceSummary(Context context) {
        XposedService service = EvoQuickFixApp.vectorService();
        if (service == null) {
            return context.getString(R.string.vector_cli_fallback);
        }
        try {
            return context.getString(R.string.vector_service_format,
                    service.getFrameworkName(), service.getFrameworkVersion(),
                    service.getApiVersion());
        } catch (Throwable t) {
            return context.getString(R.string.vector_service_unavailable);
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
