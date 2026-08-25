package com.codex.evoxquickfix;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class EvoQuickFixApp extends Application {
    private static volatile XposedService vectorService;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService service) {
                vectorService = service;
            }

            @Override
            public void onServiceDied(XposedService service) {
                if (vectorService == service) {
                    vectorService = null;
                }
            }
        });
    }

    static XposedService vectorService() {
        return vectorService;
    }
}
