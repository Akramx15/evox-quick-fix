package com.codex.evoxquickfix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

final class OperationStateStore {
    static final String OP_BACK = "back";
    static final String OP_ALL = "all";
    static final String OP_TRANSPARENCY = "transparency";
    static final String OP_CIRCLE = "circle";
    static final String OP_RESTORE = "restore";
    static final String OP_LE_AUDIO = "le_audio_music";
    static final String OP_LE_AUDIO_DISABLE = "le_audio_disable";

    enum State {
        NONE,
        PENDING,
        APPLIED,
        REBOOT_REQUIRED,
        FAILED
    }

    record Entry(String operation, State state, long updatedAt) {}

    private static final String PREFS = "operation_state_v1";
    private final SharedPreferences preferences;

    OperationStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void markPending(String operation) {
        write(operation, State.PENDING);
    }

    void markResult(String operation, OperationResult result) {
        State state = result.status == FeatureStatus.REBOOT_REQUIRED
                ? State.REBOOT_REQUIRED : State.APPLIED;
        write(operation, state);
    }

    void markFailed(String operation) {
        write(operation, State.FAILED);
    }

    Entry read() {
        String operation = preferences.getString("operation", "");
        String raw = preferences.getString("state", State.NONE.name());
        State state;
        try {
            state = State.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            state = State.NONE;
        }
        return new Entry(operation, state, preferences.getLong("updatedAt", 0L));
    }

    Entry reconcile(DiagnosticReport report) {
        Entry current = read();
        if ((OP_LE_AUDIO.equals(current.operation) || OP_LE_AUDIO_DISABLE.equals(current.operation))
                && (current.state == State.REBOOT_REQUIRED || current.state == State.PENDING)
                && System.currentTimeMillis() - current.updatedAt >= 3_000L) {
            State result = reconcileState(current.operation, report);
            if (result != current.state) write(current.operation, result);
            return read();
        }
        if (current.state != State.PENDING
                || System.currentTimeMillis() - current.updatedAt < 3_000L) {
            return current;
        }
        State result = reconcileState(current.operation, report);
        write(current.operation, result);
        return read();
    }

    static State reconcileState(String operation, DiagnosticReport report) {
        boolean transparencyApplied = report.darkTransparent && report.lightTransparent
                && report.overviewModuleInstalled;
        boolean backApplied = report.quickSearchIsHome
                && report.vectorModuleEnabled && report.vectorScopeReady;
        boolean circleSettings = report.searchAllEntrypointsEnabled
                && report.navbarLongPressEnabled;
        State circleState = circleSettings && report.contextualFeature
                ? State.APPLIED
                : (circleSettings && report.circleModuleInstalled
                ? State.REBOOT_REQUIRED : State.FAILED);
        return switch (operation) {
            case OP_LE_AUDIO -> switch (report.leAudio.state()) {
                case ACTIVE -> State.APPLIED;
                case PENDING -> State.REBOOT_REQUIRED;
                default -> State.FAILED;
            };
            case OP_LE_AUDIO_DISABLE -> switch (report.leAudio.state()) {
                case DISABLED, ABSENT -> State.APPLIED;
                case DISABLE_PENDING, REMOVING -> State.REBOOT_REQUIRED;
                default -> State.FAILED;
            };
            case OP_BACK -> backApplied ? State.APPLIED : State.FAILED;
            case OP_ALL -> transparencyApplied && backApplied
                    && circleState != State.FAILED ? circleState : State.FAILED;
            case OP_TRANSPARENCY -> transparencyApplied ? State.APPLIED : State.FAILED;
            case OP_CIRCLE -> circleState;
            case OP_RESTORE -> report.overviewModuleRemovalPending
                    || report.circleModuleRemovalPending
                    ? State.REBOOT_REQUIRED
                    : (!report.overviewModuleInstalled && !report.circleModuleInstalled
                    && !report.vectorScopeReady ? State.APPLIED : State.FAILED);
            default -> State.FAILED;
        };
    }

    @SuppressLint("ApplySharedPref")
    private void write(String operation, State state) {
        // This state must be durable before Quick Search can move the task to Home.
        preferences.edit()
                .putString("operation", operation)
                .putString("state", state.name())
                .putLong("updatedAt", System.currentTimeMillis())
                .commit();
    }
}
