package com.codex.evoxquickfix;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.codex.evoxquickfix.debloat.DebloatBackend;
import com.codex.evoxquickfix.debloat.DebloatResult;
import com.codex.evoxquickfix.debloat.DebloatTarget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DebloatActivity extends Activity {
    private static final String TAG = "EvoXDebloat";
    private static final int BG = Color.rgb(16, 20, 23);
    private static final int CARD = Color.rgb(30, 37, 41);
    private static final int TEXT = Color.rgb(238, 244, 245);
    private static final int MUTED = Color.rgb(177, 193, 196);
    private static final int ACCENT = Color.rgb(128, 203, 196);
    private static final int DANGER = Color.rgb(255, 183, 177);

    private enum Filter { ALL, PRESENT, SELECTED }
    private enum PackageUiState { ENABLED, EXTERNAL, MANAGED, ABSENT, PROTECTED, BLOCKED }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, PackageUiState> packageStates = new HashMap<>();
    private DebloatProfile profile;
    private DebloatBackend backend;
    private DiagnosticReport diagnostics;
    private LinearLayout list;
    private TextView summary;
    private TextView selectedCount;
    private TextView operation;
    private ProgressBar progress;
    private Button applyButton;
    private Button restoreButton;
    private Filter filter = Filter.ALL;
    private volatile boolean busy;
    private volatile boolean restoreReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        try {
            profile = DebloatProfileLoader.load(this);
        } catch (Exception failure) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.status_failed)
                    .setMessage(failure.getMessage())
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
        }
        setContentView(buildUi());
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (!busy) {
                        finish();
                    }
                });
        scan();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text(getString(R.string.debloat_title), 27, TEXT, true));
        TextView intro = text(getString(R.string.debloat_intro), 14, MUTED, false);
        intro.setPadding(0, dp(5), 0, dp(10));
        root.addView(intro);
        TextView limit = cardText(getString(R.string.debloat_batch_limit));
        limit.setTextColor(ACCENT);
        root.addView(limit, cardParams());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        operation = text(getString(R.string.status_idle), 14, TEXT, true);
        operation.setPadding(0, dp(10), 0, dp(5));
        root.addView(operation);
        summary = cardText(getString(R.string.debloat_scan_running));
        root.addView(summary, cardParams());
        selectedCount = text(getString(R.string.debloat_selected_count, 0), 14, ACCENT, true);
        root.addView(selectedCount);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.addView(smallButton(R.string.debloat_filter_all,
                view -> setFilter(Filter.ALL)));
        filters.addView(smallButton(R.string.debloat_filter_present,
                view -> setFilter(Filter.PRESENT)));
        filters.addView(smallButton(R.string.debloat_filter_selected,
                view -> setFilter(Filter.SELECTED)));
        root.addView(filters, cardParams());

        root.addView(actionButton(R.string.debloat_scan, view -> scan()));
        applyButton = actionButton(R.string.debloat_apply_selected,
                view -> confirmApply());
        root.addView(applyButton);
        root.addView(actionButton(R.string.debloat_clear_selection, view -> {
            selected.clear();
            renderList();
        }));
        restoreButton = actionButton(R.string.debloat_restore_owned,
                view -> confirmRestore());
        restoreButton.setTextColor(DANGER);
        root.addView(restoreButton);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        TextView emergency = cardText(getString(R.string.debloat_emergency));
        emergency.setTypeface(Typeface.MONOSPACE);
        emergency.setTextIsSelectable(true);
        root.addView(emergency, cardParams());
        return scroll;
    }

    private void scan() {
        setBusy(true, getString(R.string.debloat_scan_running));
        worker.execute(() -> {
            try {
                restoreReady = false;
                diagnostics = new DeviceDiagnostics(this).inspect();
                profile = DebloatProfileLoader.load(this);
                UserManager users = getSystemService(UserManager.class);
                CommandResult root = RootShell.run("id -u");
                boolean rootUserZero = root.ok() && "0".equals(root.output)
                        && users != null && users.isSystemUser();
                Set<String> present = Set.of();
                Set<String> disabled = Set.of();
                if (rootUserZero) {
                    present = packageSet(RootShell.run("pm list packages --user 0"),
                            "list installed packages");
                    disabled = packageSet(RootShell.run("pm list packages -d --user 0"),
                            "list disabled packages");
                    restoreReady = true;
                }
                Set<String> managed = Set.of();
                Set<String> protectedPackages = DebloatProfile.PROTECTED_PACKAGES;
                if (diagnostics.debloatSupported) {
                    backend = openBackend();
                    managed = new HashSet<>(backend.managedPackages());
                    protectedPackages = backend.protectedPackages();
                } else if (restoreReady) {
                    // Recovery remains available after a ROM/SDK/model change and does not need
                    // RoleManager, contextual search, or the current profile hash.
                    backend = DebloatBackend.openForRestore(getNoBackupFilesDir());
                    managed = new HashSet<>(backend.managedPackages());
                } else {
                    backend = null;
                }

                int installedCount = 0;
                int disabledCount = 0;
                int enabledCount = 0;
                int absentCount = 0;
                Map<String, PackageUiState> scannedStates = new HashMap<>();
                for (DebloatProfileEntry entry : profile.entries) {
                    String packageName = entry.packageName;
                    PackageUiState state;
                    if (!present.contains(packageName)) {
                        state = PackageUiState.ABSENT;
                        absentCount++;
                    } else {
                        installedCount++;
                        if (!diagnostics.debloatSupported) {
                            state = PackageUiState.BLOCKED;
                        } else if (managed.contains(packageName)) {
                            state = PackageUiState.MANAGED;
                            disabledCount++;
                        } else if (disabled.contains(packageName)) {
                            state = PackageUiState.EXTERNAL;
                            disabledCount++;
                        } else if (protectedPackages.contains(packageName)) {
                            state = PackageUiState.PROTECTED;
                            enabledCount++;
                        } else {
                            state = PackageUiState.ENABLED;
                            enabledCount++;
                        }
                    }
                    scannedStates.put(packageName, state);
                }
                int finalInstalled = installedCount;
                int finalDisabled = disabledCount;
                int finalEnabled = enabledCount;
                int finalAbsent = absentCount;
                runOnUiThread(() -> {
                    packageStates.clear();
                    packageStates.putAll(scannedStates);
                    selected.removeIf(packageName ->
                            packageStates.get(packageName) != PackageUiState.ENABLED);
                    summary.setText(getString(R.string.debloat_summary,
                            finalInstalled, finalDisabled, finalEnabled, finalAbsent));
                    operation.setText(diagnostics.debloatSupported
                            ? R.string.status_ready : R.string.status_blocked);
                    setBusy(false, null);
                    renderList();
                });
            } catch (Throwable failure) {
                showFailure(failure);
            }
        });
    }

    private DebloatBackend openBackend() {
        return DebloatBackend.open(getNoBackupFilesDir(),
                DebloatProfile.EXPECTED_PACKAGE_SET_SHA256,
                DebloatProfile.PROTECTED_PACKAGES);
    }

    private static Set<String> packageSet(CommandResult result, String description) {
        result.requireSuccess(description);
        Set<String> values = new HashSet<>();
        for (String line : result.output.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package:")) {
                values.add(trimmed.substring("package:".length()));
            }
        }
        return values;
    }

    private void setFilter(Filter filter) {
        this.filter = filter;
        renderList();
    }

    private void renderList() {
        if (profile == null || list == null) {
            return;
        }
        list.removeAllViews();
        boolean arabic = getResources().getConfiguration().getLocales().get(0)
                .getLanguage().equals("ar");
        for (DebloatProfileEntry entry : profile.entries) {
            PackageUiState state = packageStates.getOrDefault(
                    entry.packageName, PackageUiState.BLOCKED);
            if (filter == Filter.PRESENT && state == PackageUiState.ABSENT) {
                continue;
            }
            if (filter == Filter.SELECTED && !selected.contains(entry.packageName)) {
                continue;
            }
            list.addView(packageCard(entry, state, arabic), cardParams());
        }
        selectedCount.setText(getString(R.string.debloat_selected_count, selected.size()));
        applyButton.setEnabled(!busy && diagnostics != null
                && diagnostics.debloatSupported && !selected.isEmpty());
        restoreButton.setEnabled(!busy && restoreReady);
    }

    private View packageCard(DebloatProfileEntry entry, PackageUiState state, boolean arabic) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(CARD);
        card.setPadding(dp(13), dp(11), dp(13), dp(11));

        CheckBox choice = new CheckBox(this);
        choice.setText(choiceLabel(entry, arabic, selected.contains(entry.packageName)));
        choice.setTextColor(TEXT);
        choice.setTextSize(16);
        choice.setChecked(selected.contains(entry.packageName));
        choice.setEnabled(state == PackageUiState.ENABLED && !busy);
        choice.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                selected.add(entry.packageName);
            } else {
                selected.remove(entry.packageName);
            }
            button.setText(choiceLabel(entry, arabic, checked));
            selectedCount.setText(getString(R.string.debloat_selected_count, selected.size()));
            applyButton.setEnabled(!busy && !selected.isEmpty());
        });
        card.addView(choice);

        card.addView(detail(getString(R.string.debloat_package_id, entry.packageName), true));
        card.addView(detail(localized(entry.function, arabic), false));
        card.addView(detail(localized(entry.impact, arabic), false));
        TextView risk = detail(getString(R.string.debloat_risk, riskLabel(entry.risk)), true);
        if (entry.dangerous) {
            risk.setTextColor(DANGER);
        }
        card.addView(risk);
        card.addView(detail(getString(R.string.debloat_current_state, stateLabel(state)), true));
        return card;
    }

    private String choiceLabel(DebloatProfileEntry entry, boolean arabic, boolean disable) {
        return getString(R.string.debloat_choice_format, localized(entry.name, arabic),
                getString(disable ? R.string.debloat_disable : R.string.debloat_keep));
    }

    private String localized(DebloatLocalizedText value, boolean arabic) {
        return arabic ? value.arabic : value.english;
    }

    private String riskLabel(DebloatRisk risk) {
        return getString(switch (risk) {
            case LOW -> R.string.debloat_risk_low;
            case MEDIUM -> R.string.debloat_risk_medium;
            case HIGH -> R.string.debloat_risk_high;
            case CRITICAL -> R.string.debloat_risk_critical;
        });
    }

    private String stateLabel(PackageUiState state) {
        return getString(switch (state) {
            case ENABLED -> R.string.debloat_enabled;
            case EXTERNAL -> R.string.debloat_external;
            case MANAGED -> R.string.debloat_managed;
            case ABSENT -> R.string.debloat_absent;
            case PROTECTED -> R.string.debloat_protected;
            case BLOCKED -> R.string.status_blocked;
        });
    }

    private void confirmApply() {
        if (selected.isEmpty()) {
            showMessage(R.string.debloat_empty);
            return;
        }
        List<DebloatProfileEntry> entries = selectedEntries();
        long dangerous = entries.stream().filter(entry -> entry.dangerous).count();
        if (dangerous > 0 && (entries.size() != 1 || dangerous != 1)) {
            showMessage(R.string.debloat_mixed_danger);
            return;
        }
        if (entries.size() > DebloatBackend.MAX_BATCH_SIZE) {
            showMessage(R.string.debloat_too_many);
            return;
        }
        if (dangerous == 1) {
            DebloatProfileEntry entry = entries.get(0);
            EditText typed = new EditText(this);
            typed.setSingleLine(true);
            typed.setHint(entry.packageName);
            int pad = dp(18);
            typed.setPadding(pad, pad, pad, pad);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.debloat_danger_title)
                    .setMessage(getString(R.string.debloat_danger_message, entry.packageName))
                    .setView(typed)
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .setPositiveButton(R.string.debloat_disable, (dialog, which) -> {
                        String value = typed.getText().toString().trim();
                        if (!entry.packageName.equals(value)) {
                            showMessage(R.string.debloat_type_mismatch);
                        } else {
                            applyEntries(entries, value);
                        }
                    }).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.debloat_confirm_title)
                .setMessage(R.string.debloat_confirm_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.debloat_disable,
                        (dialog, which) -> applyEntries(entries, ""))
                .show();
    }

    private List<DebloatProfileEntry> selectedEntries() {
        List<DebloatProfileEntry> entries = new ArrayList<>();
        for (String packageName : selected) {
            DebloatProfileEntry entry = profile.find(packageName);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    @SuppressWarnings("try")
    private void applyEntries(List<DebloatProfileEntry> entries, String typedId) {
        setBusy(true, getString(R.string.operation_running,
                getString(R.string.debloat_apply_selected)));
        worker.execute(() -> {
            try (OperationCoordinator.Lease ignored =
                         OperationCoordinator.acquire("debloat activity apply")) {
                DiagnosticReport current = new DeviceDiagnostics(this).inspect();
                if (!current.debloatSupported) {
                    throw new SecurityException(getString(R.string.status_blocked));
                }
                backend = openBackend();
                DebloatHealthSnapshot health = DebloatHealthSnapshot.capture();
                List<DebloatTarget> targets = entries.stream()
                        .map(entry -> new DebloatTarget(entry.packageName, entry.dangerous))
                        .toList();
                DebloatResult result = backend.apply(targets, typedId, health::verifyCurrent);
                runOnUiThread(() -> {
                    selected.clear();
                    operation.setText(getString(R.string.debloat_apply_success,
                            result.changed.size(),
                            result.external.size() + result.absent.size()));
                    scan();
                });
            } catch (Throwable failure) {
                showFailure(failure);
            }
        });
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.debloat_restore_owned)
                .setMessage(R.string.debloat_emergency)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_restore, (dialog, which) -> restoreOwned())
                .show();
    }

    @SuppressWarnings("try")
    private void restoreOwned() {
        setBusy(true, getString(R.string.operation_running,
                getString(R.string.debloat_restore_owned)));
        worker.execute(() -> {
            try (OperationCoordinator.Lease ignored =
                         OperationCoordinator.acquire("debloat activity restore")) {
                if (!restoreEnvironmentReady()) {
                    throw new SecurityException(getString(R.string.status_blocked));
                }
                backend = DebloatBackend.openForRestore(getNoBackupFilesDir());
                DebloatResult result = backend.restoreAll();
                runOnUiThread(() -> {
                    operation.setText(getString(
                            R.string.debloat_restore_success, result.changed.size()));
                    scan();
                    worker.execute(this::runBestEffortPostRestoreHealth);
                });
            } catch (Throwable failure) {
                showFailure(failure);
            }
        });
    }

    private void runBestEffortPostRestoreHealth() {
        try {
            DebloatHealthSnapshot.capture();
        } catch (Throwable degradedHealth) {
            // Exact baseline restoration is the recovery action. A broken optional service must
            // never prevent it, delay the success result, or cause packages to be disabled again.
            Log.w(TAG, "post-restore health check is degraded", degradedHealth);
        }
    }

    private boolean restoreEnvironmentReady() {
        UserManager users = getSystemService(UserManager.class);
        CommandResult root = RootShell.run("id -u");
        if (!root.ok() || !"0".equals(root.output)
                || users == null || !users.isSystemUser()) {
            return false;
        }
        return RootShell.run("pm list packages --user 0").ok();
    }

    private void showMessage(int resource) {
        new AlertDialog.Builder(this)
                .setMessage(resource)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showFailure(Throwable failure) {
        runOnUiThread(() -> {
            setBusy(false, null);
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            operation.setText(getString(R.string.operation_failed, message));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.status_failed)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        if (progress != null) {
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        }
        if (message != null && operation != null) {
            operation.setText(message);
        }
        if (applyButton != null) {
            applyButton.setEnabled(!busy && !selected.isEmpty());
        }
        if (restoreButton != null) {
            restoreButton.setEnabled(!busy && restoreReady);
        }
    }

    private Button actionButton(int label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundColor(CARD);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        params.setMargins(0, dp(5), 0, dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private Button smallButton(int label, View.OnClickListener listener) {
        Button button = actionButton(label, listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView detail(String value, boolean bold) {
        TextView text = text(value, 13, bold ? ACCENT : MUTED, bold);
        text.setPadding(dp(4), dp(3), dp(4), dp(3));
        text.setTextIsSelectable(true);
        return text;
    }

    private TextView cardText(String value) {
        TextView text = text(value, 14, TEXT, false);
        text.setBackgroundColor(CARD);
        text.setPadding(dp(13), dp(13), dp(13), dp(13));
        text.setLineSpacing(0f, 1.12f);
        return text;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
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

    /** Invariants captured before a batch and checked after every package mutation. */
    private record DebloatHealthSnapshot(
            String home,
            String dialer,
            String sms,
            String browser,
            String assistantRole,
            String defaultIme,
            String assistantSetting,
            String voiceInteractionService,
            String provider,
            String feature,
            String searchEntryPoints,
            String longPress,
            boolean contextualService,
            Set<String> capabilityPackages) {
        private static final Pattern PACKAGE_ID = Pattern.compile(
                "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+");
        private static final Pattern COMPONENT = Pattern.compile(
                "([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/");

        DebloatHealthSnapshot {
            capabilityPackages = Set.copyOf(capabilityPackages);
        }

        static DebloatHealthSnapshot capture() {
            String home = role("HOME", true);
            String dialer = role("DIALER", false);
            String sms = role("SMS", false);
            String browser = role("BROWSER", false);
            String assistantRole = role("ASSISTANT", false);
            String defaultIme = read(
                    "settings --user 0 get secure default_input_method",
                    "read default input method", false);
            String assistantSetting = read(
                    "settings --user 0 get secure assistant",
                    "read current assistant", false);
            String voiceInteraction = read(
                    "settings --user 0 get secure voice_interaction_service",
                    "read voice interaction service", false);
            String provider = read(
                    "cmd package query-activities --brief --user 0 -a "
                            + AppConstants.CONTEXTUAL_ACTION,
                    "read contextual provider", false);

            Set<String> present = packageSet(RootShell.run("pm list packages --user 0"),
                    "read health-check packages");
            Set<String> disabled = packageSet(
                    RootShell.run("pm list packages -d --user 0"),
                    "read disabled health-check packages");
            requireSystemUi(present, disabled);
            boolean contextualService = RootShell.run(
                    "service list | grep -F 'contextual_search:' >/dev/null").ok();
            String feature = read("pm has-feature " + AppConstants.CONTEXTUAL_FEATURE,
                    "read contextual feature", true);
            String entryPoints = read(
                    "settings --user 0 get secure search_all_entrypoints_enabled",
                    "read Circle entry points", false);
            String longPress = read(
                    "settings --user 0 get system navbar_long_press_gesture",
                    "read Circle long press", false);

            Set<String> holders = new HashSet<>();
            addRolePackages(holders, home);
            addRolePackages(holders, dialer);
            addRolePackages(holders, sms);
            addRolePackages(holders, browser);
            addRolePackages(holders, assistantRole);
            addComponentPackages(holders, defaultIme);
            addComponentPackages(holders, assistantSetting);
            addComponentPackages(holders, voiceInteraction);
            addComponentPackages(holders, provider);
            requireEnabled(holders, present, disabled);

            return new DebloatHealthSnapshot(
                    normalize(home), normalize(dialer), normalize(sms), normalize(browser),
                    normalize(assistantRole), normalize(defaultIme), normalize(assistantSetting),
                    normalize(voiceInteraction), normalize(provider), normalize(feature),
                    normalize(entryPoints), normalize(longPress), contextualService, holders);
        }

        void verifyCurrent() {
            DebloatHealthSnapshot after = capture();
            if (!equals(after)) {
                throw new IllegalStateException(
                        "a protected role, IME, contextual provider or Circle state changed");
            }
        }

        private static String role(String role, boolean requireHolder) {
            return read("cmd role get-role-holders --user 0 android.app.role." + role,
                    "read " + role + " role holders", requireHolder);
        }

        private static void requireSystemUi(Set<String> present, Set<String> disabled) {
            if (!present.contains(AppConstants.SYSTEM_UI_PACKAGE)
                    || disabled.contains(AppConstants.SYSTEM_UI_PACKAGE)) {
                throw new IllegalStateException("SystemUI is unavailable or disabled");
            }
        }

        private static void requireEnabled(Set<String> packages, Set<String> present,
                                           Set<String> disabled) {
            if (packages.isEmpty()) {
                return;
            }
            for (String packageName : packages) {
                if (!present.contains(packageName) || disabled.contains(packageName)) {
                    throw new IllegalStateException(
                            "protected capability package is unavailable: " + packageName);
                }
            }
        }

        private static void addRolePackages(Set<String> packages, String output) {
            Matcher matcher = PACKAGE_ID.matcher(output);
            while (matcher.find()) {
                String candidate = matcher.group();
                if (!candidate.startsWith("android.app.role.")) {
                    packages.add(candidate);
                }
            }
        }

        private static void addComponentPackages(Set<String> packages, String output) {
            Matcher matcher = COMPONENT.matcher(output);
            while (matcher.find()) {
                packages.add(matcher.group(1));
            }
        }

        private static String read(String command, String description,
                                   boolean requireNonBlank) {
            CommandResult result = RootShell.run(command);
            result.requireSuccess(description);
            if (requireNonBlank && result.output.isBlank()) {
                throw new IllegalStateException(description + " returned no value");
            }
            return result.output;
        }

        private static String normalize(String value) {
            return String.join("\n", new TreeSet<>(value.lines()
                    .map(String::trim).filter(line -> !line.isEmpty()).toList()));
        }
    }
}
