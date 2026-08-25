package com.codex.evoxquickfix;

import java.util.ArrayList;
import java.util.List;

final class DiagnosticReport {
    boolean root;
    boolean deviceGate;
    boolean launcherPresent;
    boolean quickSearchPresent;
    boolean quickSearchCompatible;
    boolean quickSearchIsHome;
    boolean googleProvider;
    boolean vectorReady;
    boolean magicMountReady;
    boolean contextualFeature;
    boolean contextualService;
    boolean darkResource;
    boolean lightResource;
    boolean darkTransparent;
    boolean lightTransparent;
    boolean vectorModuleEnabled;
    boolean vectorScopeReady;
    boolean overviewModuleInstalled;
    boolean circleModuleInstalled;
    String quickSearchHash = "";
    String quickSearchCertificate = "";
    String vectorStatus = "";
    final List<String> notes = new ArrayList<>();

    boolean baseSupported() {
        return root && deviceGate && launcherPresent && quickSearchPresent && googleProvider
                && vectorReady && magicMountReady && contextualService;
    }

    String toArabicText() {
        String ok = "✓ ";
        String bad = "✗ ";
        StringBuilder text = new StringBuilder();
        text.append(root ? ok : bad).append("Root\n");
        text.append(deviceGate ? ok : bad).append("SM-S918B / Android 16 / User 0\n");
        text.append(launcherPresent ? ok : bad).append("Launcher3 Quickstep\n");
        text.append(quickSearchPresent ? ok : bad).append("Quick Search مثبت\n");
        text.append(quickSearchCompatible ? ok : bad).append("Quick Search 4.1.1 (78) مطابق\n");
        text.append(quickSearchIsHome ? ok : bad).append("Quick Search هو HOME\n");
        text.append(vectorReady ? ok : bad).append("Vector API 102\n");
        text.append(magicMountReady ? ok : bad).append("Magic Mount-rs\n");
        text.append(googleProvider ? ok : bad).append("Google Contextual Search provider\n");
        text.append(contextualService ? ok : bad).append("خدمة contextual_search\n");
        text.append(contextualFeature ? ok : "○ ").append("ميزة CONTEXTUAL_SEARCH")
                .append(contextualFeature ? " مفعلة" : " تنتظر الإصلاح/Restart").append("\n");
        text.append(darkTransparent ? ok : "○ ").append("شفافية Recent Apps الداكنة\n");
        text.append(lightTransparent ? ok : "○ ").append("شفافية Recent Apps الفاتحة\n");
        text.append(overviewModuleInstalled ? ok : "○ ")
                .append("استمرار الشفافية بعد Restart\n");
        text.append(vectorModuleEnabled && vectorScopeReady ? ok : "○ ")
                .append("Back Guard عبر Vector\n");
        text.append(circleModuleInstalled ? ok : "○ ").append("وحدة Circle systemless\n");
        for (String note : notes) {
            text.append("• ").append(note).append('\n');
        }
        return text.toString().trim();
    }
}
