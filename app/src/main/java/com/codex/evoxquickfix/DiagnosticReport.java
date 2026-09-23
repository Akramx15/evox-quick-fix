package com.codex.evoxquickfix;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

final class DiagnosticReport {
    boolean root;
    boolean systemUser;
    boolean singleUserProfile;
    boolean modelSupported;
    boolean android16;
    boolean deviceGate;
    boolean exactEnvironmentGate;
    boolean kernelSuReady;
    boolean magiskPresent;
    boolean launcherPresent;
    boolean quickSearchPresent;
    boolean quickSearchCompatible;
    boolean quickSearchIsHome;
    boolean documentsUiReady;
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
    boolean overviewModuleRemovalPending;
    boolean circleModuleRemovalPending;
    boolean searchAllEntrypointsEnabled;
    boolean navbarLongPressEnabled;
    boolean transparencySupported;
    boolean backGuardSupported;
    boolean circleSupported;
    boolean debloatSupported;
    boolean debloatRestoreSupported;
    LeAudioStatus leAudio = LeAudioStatus.unavailable();
    String contextualProviderPackage = "";
    String quickSearchHash = "";
    String quickSearchCertificate = "";
    String vectorStatus = "";
    final List<String> notes = new ArrayList<>();

    boolean baseSupported() {
        return transparencySupported && backGuardSupported && circleSupported;
    }

    String toDisplayText(Context context) {
        StringBuilder text = new StringBuilder();
        append(text, root, context.getString(R.string.check_root));
        append(text, deviceGate,
                context.getString(R.string.check_device));
        append(text, kernelSuReady && !magiskPresent,
                context.getString(R.string.check_kernelsu));
        append(text, launcherPresent, context.getString(R.string.check_launcher));
        append(text, quickSearchPresent, context.getString(R.string.check_quicksearch));
        append(text, quickSearchCompatible, context.getString(R.string.check_quicksearch_build));
        append(text, quickSearchIsHome, context.getString(R.string.check_quicksearch_home));
        append(text, documentsUiReady, context.getString(R.string.check_documentsui));
        append(text, vectorReady, context.getString(R.string.check_vector));
        append(text, magicMountReady || contextualFeature,
                context.getString(R.string.check_magic_mount));
        append(text, googleProvider, context.getString(R.string.check_google_provider));
        append(text, contextualService, context.getString(R.string.check_contextual_service));
        appendOptional(text, contextualFeature,
                context.getString(R.string.check_contextual_feature));
        appendOptional(text, darkTransparent && lightTransparent,
                context.getString(R.string.check_transparency));
        appendOptional(text, overviewModuleInstalled,
                context.getString(R.string.check_transparency_persist));
        appendOptional(text, vectorModuleEnabled && vectorScopeReady,
                context.getString(R.string.check_back_guard));
        appendOptional(text, searchAllEntrypointsEnabled && navbarLongPressEnabled
                        && contextualFeature
                        && (circleModuleInstalled || !needsCircleModule()),
                context.getString(R.string.check_circle));
        text.append('\n');
        appendFeature(context, text, transparencySupported,
                context.getString(R.string.feature_transparency));
        appendFeature(context, text, backGuardSupported,
                context.getString(R.string.feature_back_guard));
        appendFeature(context, text, backGuardSupported,
                context.getString(R.string.feature_apk_results));
        appendFeature(context, text, circleSupported,
                context.getString(R.string.feature_circle));
        appendFeature(context, text, debloatSupported,
                context.getString(R.string.feature_debloat));
        for (String note : notes) {
            text.append("• ").append(noteText(context, note)).append('\n');
        }
        return text.toString().trim();
    }

    boolean needsCircleModule() {
        return !contextualFeature;
    }

    private static void append(StringBuilder text, boolean value, String label) {
        text.append(value ? "✓ " : "✗ ").append(label).append('\n');
    }

    private static void appendOptional(StringBuilder text, boolean value, String label) {
        text.append(value ? "✓ " : "○ ").append(label).append('\n');
    }

    private static void appendFeature(Context context, StringBuilder text,
                                      boolean value, String label) {
        text.append(context.getString(value ? R.string.status_ready : R.string.status_blocked))
                .append(" — ").append(label).append('\n');
    }

    private static String noteText(Context context, String key) {
        return switch (key) {
            case "unsupported_device" -> context.getString(R.string.note_unsupported_device);
            case "magisk_blocked" -> context.getString(R.string.note_magisk_blocked);
            case "quicksearch_mismatch" -> context.getString(R.string.note_quicksearch_mismatch);
            case "contextual_already_present" ->
                    context.getString(R.string.note_contextual_present);
            case "quicksearch_check_failed" ->
                    context.getString(R.string.note_quicksearch_check_failed);
            default -> key;
        };
    }
}
