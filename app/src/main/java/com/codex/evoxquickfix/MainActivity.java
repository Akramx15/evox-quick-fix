package com.codex.evoxquickfix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 20, 23);
    private static final int CARD = Color.rgb(30, 37, 41);
    private static final int TEXT = Color.rgb(238, 244, 245);
    private static final int MUTED = Color.rgb(177, 193, 196);
    private static final int ACCENT = Color.rgb(128, 203, 196);
    private static final int DANGER = Color.rgb(255, 183, 177);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Button> actionButtons = new ArrayList<>();
    private FixManager fixes;
    private LeAudioManager leAudio;
    private OperationStateStore operationState;
    private TextView deviceStatus;
    private TextView featureStatus;
    private TextView operationStatus;
    private ProgressBar progress;
    private Button applyAllButton;
    private Button transparencyButton;
    private Button backButton;
    private Button circleButton;
    private Button debloatButton;
    private Button leAudioButton;
    private Button leAudioDisableButton;
    private TextView leAudioStatus;
    private DiagnosticReport lastReport;
    private volatile boolean busy;
    private boolean resumedOnce;
    private boolean restartPromptShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        fixes = new FixManager(this);
        leAudio = new LeAudioManager(this);
        operationState = new OperationStateStore(this);
        setContentView(buildUi());
        applyAvailability(new DiagnosticReport());
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (!busy) {
                        finish();
                    }
                });
        showOnboardingIfNeeded();
        refreshDiagnostics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resumedOnce && operationState.read().state() == OperationStateStore.State.PENDING) {
            refreshDiagnostics();
        }
        resumedOnce = true;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(getString(R.string.app_name), 28, TEXT, true);
        root.addView(title);

        TextView subtitle = text(getString(R.string.home_subtitle), 15, MUTED, false);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle);

        TextView safety = cardText(getString(R.string.home_safety));
        safety.setTextColor(ACCENT);
        root.addView(safety, cardParams());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        progressParams.setMargins(0, dp(12), 0, dp(8));
        root.addView(progress, progressParams);

        operationStatus = text(getString(R.string.status_idle), 15, TEXT, true);
        operationStatus.setPadding(dp(4), dp(8), dp(4), dp(8));
        root.addView(operationStatus);

        root.addView(sectionTitle(getString(R.string.section_diagnostics)));
        featureStatus = cardText(getString(R.string.status_inspecting));
        featureStatus.setTextIsSelectable(true);
        root.addView(featureStatus, cardParams());

        deviceStatus = cardText(getString(R.string.status_inspecting));
        deviceStatus.setTextIsSelectable(true);
        deviceStatus.setMovementMethod(new ScrollingMovementMethod());
        root.addView(deviceStatus, cardParams());

        root.addView(sectionTitle(getString(R.string.section_actions)));
        root.addView(actionButton(getString(R.string.action_inspect),
                view -> refreshDiagnostics()));
        applyAllButton = actionButton(getString(R.string.action_apply_all),
                view -> confirmApplyAll());
        root.addView(applyAllButton);
        transparencyButton = actionButton(getString(R.string.action_transparency),
                view -> runOperation(OperationStateStore.OP_TRANSPARENCY,
                        R.string.action_transparency, fixes::applyTransparency, false));
        root.addView(transparencyButton);
        backButton = actionButton(getString(R.string.action_back_guard),
                view -> confirmDepartureThenRun(OperationStateStore.OP_BACK,
                        R.string.action_back_guard, fixes::applyBackGuard));
        root.addView(backButton);
        circleButton = actionButton(getString(R.string.action_circle),
                view -> runOperation(OperationStateStore.OP_CIRCLE,
                        R.string.action_circle, fixes::applyCircleToSearch, false));
        root.addView(circleButton);

        root.addView(sectionTitle(getString(R.string.feature_le_audio)));
        root.addView(cardText(getString(R.string.le_audio_description)), cardParams());
        leAudioStatus = cardText(getString(R.string.status_inspecting));
        root.addView(leAudioStatus, cardParams());
        leAudioButton = actionButton(getString(R.string.action_le_audio),
                view -> confirmLeAudio(false));
        root.addView(leAudioButton);
        leAudioDisableButton = actionButton(getString(R.string.action_le_audio_disable),
                view -> confirmLeAudio(true));
        leAudioDisableButton.setTextColor(DANGER);
        root.addView(leAudioDisableButton);
        root.addView(sectionTitle(getString(R.string.section_tools)));
        debloatButton = actionButton(getString(R.string.action_debloat),
                view -> startActivity(new Intent(this, DebloatActivity.class)));
        root.addView(debloatButton);
        root.addView(actionButton(getString(R.string.action_help),
                view -> startActivity(new Intent(this, HelpActivity.class))));
        root.addView(actionButton(getString(R.string.action_reboot),
                view -> confirmReboot()));

        Button restore = actionButton(getString(R.string.action_restore),
                view -> confirmRestore());
        restore.setTextColor(DANGER);
        root.addView(restore);

        root.addView(sectionTitle(getString(R.string.section_recovery)));
        TextView emergency = cardText(getString(R.string.emergency_core));
        emergency.setTextIsSelectable(true);
        emergency.setTypeface(Typeface.MONOSPACE);
        root.addView(emergency, cardParams());

        TextView footer = text(getString(R.string.footer_version), 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        root.addView(footer);
        return scroll;
    }

    private void showOnboardingIfNeeded() {
        if (getSharedPreferences("onboarding", MODE_PRIVATE)
                .getBoolean("completed_v1", false)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.first_run_title)
                .setMessage(R.string.first_run_message)
                .setNeutralButton(R.string.open_help, (dialog, which) ->
                        startActivity(new Intent(this, HelpActivity.class)))
                .setPositiveButton(R.string.continue_label, (dialog, which) ->
                        getSharedPreferences("onboarding", MODE_PRIVATE).edit()
                                .putBoolean("completed_v1", true).apply())
                .setCancelable(false)
                .show();
    }

    private void confirmApplyAll() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_apply_all_title)
                .setMessage(R.string.dialog_apply_all_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_apply, (dialog, which) ->
                        confirmDepartureThenRun(OperationStateStore.OP_ALL,
                                R.string.action_apply_all, fixes::applyAll))
                .show();
    }

    private void confirmDepartureThenRun(String operationId, int labelRes,
                                         Callable<OperationResult> operation) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_departure_title)
                .setMessage(R.string.dialog_departure_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_apply, (dialog, which) ->
                        runOperation(operationId, labelRes, operation, true))
                .show();
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_restore_title)
                .setMessage(R.string.dialog_restore_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_restore, (dialog, which) ->
                        runOperation(OperationStateStore.OP_RESTORE,
                                R.string.action_restore, fixes::restoreRomBehavior, false))
                .show();
    }

    private void confirmLeAudio(boolean disable) {
        new AlertDialog.Builder(this)
                .setTitle(disable ? R.string.action_le_audio_disable : R.string.action_le_audio)
                .setMessage(disable ? R.string.le_audio_disable_confirm : R.string.le_audio_confirm)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(disable ? R.string.dialog_restore : R.string.dialog_apply,
                        (dialog, which) -> runOperation(
                                disable ? OperationStateStore.OP_LE_AUDIO_DISABLE
                                        : OperationStateStore.OP_LE_AUDIO,
                                disable ? R.string.action_le_audio_disable : R.string.action_le_audio,
                                disable ? leAudio::disable : leAudio::apply, false))
                .show();
    }

    private void confirmReboot() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_reboot_title)
                .setMessage(R.string.dialog_reboot_message)
                .setNegativeButton(R.string.dialog_later, null)
                .setPositiveButton(R.string.dialog_reboot_now, (dialog, which) -> {
                    operationStatus.setText(R.string.dialog_reboot_title);
                    worker.execute(() -> {
                        try {
                            fixes.rebootDevice();
                        } catch (Throwable failure) {
                            showFailure(R.string.action_reboot, failure, "");
                        }
                    });
                })
                .show();
    }

    private void refreshDiagnostics() {
        setBusy(true, getString(R.string.status_inspecting));
        worker.execute(() -> {
            try {
                DiagnosticReport report = fixes.inspect();
                OperationStateStore.Entry stored = operationState.reconcile(report);
                runOnUiThread(() -> {
                    lastReport = report;
                    deviceStatus.setText(getString(R.string.diagnostic_with_vector,
                            report.toDisplayText(this), VectorBridge.serviceSummary(this)));
                    featureStatus.setText(featureCards(report));
                    operationStatus.setText(report.transparencySupported
                            || report.backGuardSupported || report.circleSupported
                            || report.debloatSupported
                            || report.leAudio.canApply() || report.leAudio.canDisable()
                            ? R.string.status_device_ready : R.string.status_review);
                    setBusy(false, null);
                    applyAvailability(report);
                    showStoredOperation(stored);
                });
            } catch (Throwable failure) {
                showFailure(R.string.action_inspect, failure, "");
            }
        });
    }

    private String featureCards(DiagnosticReport report) {
        return featureLine(R.string.feature_transparency, report.transparencySupported)
                + "\n" + featureLine(R.string.feature_back_guard, report.backGuardSupported)
                + "\n" + featureLine(R.string.feature_apk_results,
                report.backGuardSupported)
                + "\n" + featureLine(R.string.feature_circle, report.circleSupported)
                + "\n" + featureLine(R.string.feature_debloat, report.debloatSupported);
    }

    private String featureLine(int labelRes, boolean ready) {
        return (ready ? "✓ " + getString(R.string.status_ready)
                : "✗ " + getString(R.string.status_blocked))
                + " — " + getString(labelRes);
    }

    private void applyAvailability(DiagnosticReport report) {
        applyAllButton.setEnabled(report.baseSupported());
        transparencyButton.setEnabled(report.transparencySupported);
        backButton.setEnabled(report.backGuardSupported);
        circleButton.setEnabled(report.circleSupported);
        debloatButton.setEnabled(report.debloatSupported || report.debloatRestoreSupported);
        leAudioButton.setEnabled(report.leAudio.canApply());
        leAudioDisableButton.setEnabled(report.leAudio.canDisable());
        leAudioStatus.setText(report.leAudio.state().label);
    }

    private void runOperation(String operationId, int labelRes,
                              Callable<OperationResult> operation,
                              boolean expectedDeparture) {
        operationState.markPending(operationId);
        restartPromptShown = false;
        String label = getString(labelRes);
        setBusy(true, getString(R.string.operation_running, label));
        if (expectedDeparture) {
            operationStatus.setText(R.string.operation_departure_pending);
        }
        worker.execute(() -> {
            try {
                OperationResult result = operation.call();
                operationState.markResult(operationId, result);
                DiagnosticReport report = fixes.inspect();
                runOnUiThread(() -> {
                    lastReport = report;
                    deviceStatus.setText(getString(R.string.diagnostic_with_vector,
                            report.toDisplayText(this), VectorBridge.serviceSummary(this)));
                    featureStatus.setText(featureCards(report));
                    operationStatus.setText(getString(R.string.operation_completed,
                            getString(result.status.labelRes), label));
                    setBusy(false, null);
                    applyAvailability(report);
                    if (result.status == FeatureStatus.REBOOT_REQUIRED) {
                        showRestartPrompt();
                    }
                });
            } catch (Throwable failure) {
                operationState.markFailed(operationId);
                showFailure(labelRes, failure, operationId);
            }
        });
    }

    private void showStoredOperation() {
        showStoredOperation(operationState.read());
    }

    private void showStoredOperation(OperationStateStore.Entry entry) {
        if (entry.state() == OperationStateStore.State.NONE) {
            return;
        }
        if (entry.state() == OperationStateStore.State.PENDING) {
            operationStatus.setText(R.string.operation_departure_pending);
            return;
        }
        int statusRes = switch (entry.state()) {
            case APPLIED -> R.string.status_applied;
            case REBOOT_REQUIRED -> R.string.status_reboot_required;
            case FAILED -> R.string.status_failed;
            default -> R.string.status_idle;
        };
        operationStatus.setText(getString(R.string.operation_previous_result,
                operationLabel(entry.operation()), getString(statusRes)));
        if (entry.state() == OperationStateStore.State.REBOOT_REQUIRED) {
            showRestartPrompt();
        }
    }

    private String operationLabel(String operation) {
        return getString(switch (operation) {
            case OperationStateStore.OP_BACK -> R.string.action_back_guard;
            case OperationStateStore.OP_ALL -> R.string.action_apply_all;
            case OperationStateStore.OP_TRANSPARENCY -> R.string.action_transparency;
            case OperationStateStore.OP_CIRCLE -> R.string.action_circle;
            case OperationStateStore.OP_RESTORE -> R.string.action_restore;
            case OperationStateStore.OP_LE_AUDIO -> R.string.action_le_audio;
            case OperationStateStore.OP_LE_AUDIO_DISABLE -> R.string.action_le_audio_disable;
            default -> R.string.app_name;
        });
    }

    private void showRestartPrompt() {
        if (isFinishing() || isDestroyed() || restartPromptShown) {
            return;
        }
        restartPromptShown = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.status_reboot_required)
                .setMessage(R.string.operation_reboot_message)
                .setNegativeButton(R.string.dialog_later, null)
                .setPositiveButton(R.string.dialog_reboot_now,
                        (dialog, which) -> worker.execute(fixes::rebootDevice))
                .show();
    }

    private void showFailure(int labelRes, Throwable failure, String operationId) {
        runOnUiThread(() -> {
            setBusy(false, null);
            if (lastReport != null) {
                applyAvailability(lastReport);
            } else {
                applyAvailability(new DiagnosticReport());
            }
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            operationStatus.setText(getString(R.string.operation_failed, message));
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.operation_failed_title, getString(labelRes)))
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        for (Button button : actionButtons) {
            button.setEnabled(!busy);
            button.setAlpha(busy ? 0.55f : 1f);
        }
        if (message != null) {
            operationStatus.setText(message);
        }
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(CARD);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        params.setMargins(0, dp(6), 0, dp(6));
        button.setLayoutParams(params);
        actionButtons.add(button);
        return button;
    }

    private TextView sectionTitle(String value) {
        TextView text = text(value, 18, ACCENT, true);
        text.setPadding(0, dp(22), 0, dp(4));
        return text;
    }

    private TextView cardText(String value) {
        TextView text = text(value, 14, TEXT, false);
        text.setBackgroundColor(CARD);
        text.setPadding(dp(14), dp(14), dp(14), dp(14));
        text.setLineSpacing(0f, 1.12f);
        return text;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(7), 0, dp(7));
        return params;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setGravity(Gravity.START);
        text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
