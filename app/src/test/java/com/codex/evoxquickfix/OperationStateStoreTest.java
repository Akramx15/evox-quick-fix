package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OperationStateStoreTest {
    @Test
    public void circleRequiresBothSettingsAndRuntimeFeature() {
        DiagnosticReport report = new DiagnosticReport();
        report.contextualFeature = true;
        report.searchAllEntrypointsEnabled = true;
        assertEquals(OperationStateStore.State.FAILED,
                OperationStateStore.reconcileState(OperationStateStore.OP_CIRCLE, report));

        report.navbarLongPressEnabled = true;
        assertEquals(OperationStateStore.State.APPLIED,
                OperationStateStore.reconcileState(OperationStateStore.OP_CIRCLE, report));
    }

    @Test
    public void pendingCircleModuleRequestsRestart() {
        DiagnosticReport report = new DiagnosticReport();
        report.searchAllEntrypointsEnabled = true;
        report.navbarLongPressEnabled = true;
        report.circleModuleInstalled = true;
        assertEquals(OperationStateStore.State.REBOOT_REQUIRED,
                OperationStateStore.reconcileState(OperationStateStore.OP_CIRCLE, report));
    }

    @Test
    public void transparencyAndBackRequirePersistenceAndHome() {
        DiagnosticReport report = new DiagnosticReport();
        report.darkTransparent = true;
        report.lightTransparent = true;
        assertEquals(OperationStateStore.State.FAILED,
                OperationStateStore.reconcileState(
                        OperationStateStore.OP_TRANSPARENCY, report));
        report.overviewModuleInstalled = true;
        assertEquals(OperationStateStore.State.APPLIED,
                OperationStateStore.reconcileState(
                        OperationStateStore.OP_TRANSPARENCY, report));

        report.vectorModuleEnabled = true;
        report.vectorScopeReady = true;
        assertEquals(OperationStateStore.State.FAILED,
                OperationStateStore.reconcileState(OperationStateStore.OP_BACK, report));
        report.quickSearchIsHome = true;
        assertEquals(OperationStateStore.State.APPLIED,
                OperationStateStore.reconcileState(OperationStateStore.OP_BACK, report));
    }

    @Test
    public void restoreDetectsModuleRemovalPending() {
        DiagnosticReport report = new DiagnosticReport();
        report.circleModuleRemovalPending = true;
        assertEquals(OperationStateStore.State.REBOOT_REQUIRED,
                OperationStateStore.reconcileState(OperationStateStore.OP_RESTORE, report));
    }
}
