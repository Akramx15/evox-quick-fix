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

    @Test
    public void oneBackEnableRequiresItsScopeAndTheSharedModule() {
        DiagnosticReport report = new DiagnosticReport();
        report.android16 = true;
        report.root = true;
        report.systemUser = true;
        report.vectorReady = true;
        report.vectorModulesObserved = true;
        report.vectorScopeObserved = true;
        report.heliBoardPresent = true;
        report.oneBackSupported = DeviceDiagnostics.supportsOneBack(report);
        report.vectorModuleEnabled = true;
        assertEquals(OperationStateStore.State.FAILED,
                OperationStateStore.reconcileState(OperationStateStore.OP_ONE_BACK, report));

        report.vectorImeScopeReady = true;
        assertEquals(OperationStateStore.State.APPLIED,
                OperationStateStore.reconcileState(OperationStateStore.OP_ONE_BACK, report));

        report.vectorScopeObserved = false;
        assertEquals(OperationStateStore.State.FAILED,
                OperationStateStore.reconcileState(OperationStateStore.OP_ONE_BACK, report));
        report.vectorScopeObserved = true;
        report.standaloneOneBackEnabled = true;
        assertEquals(OperationStateStore.State.FAILED,
                OperationStateStore.reconcileState(OperationStateStore.OP_ONE_BACK, report));
    }

    @Test
    public void oneBackDisableOnlyRequiresItsOwnScopeToBeAbsent() {
        DiagnosticReport report = new DiagnosticReport();
        report.root = true;
        report.systemUser = true;
        report.vectorReady = true;
        report.vectorScopeObserved = true;
        report.vectorModuleEnabled = true;
        report.vectorScopeReady = true;
        report.vectorImeScopeReady = true;
        assertEquals(OperationStateStore.State.FAILED,
                OperationStateStore.reconcileState(
                        OperationStateStore.OP_ONE_BACK_DISABLE, report));

        report.vectorImeScopeReady = false;
        assertEquals(OperationStateStore.State.APPLIED,
                OperationStateStore.reconcileState(
                        OperationStateStore.OP_ONE_BACK_DISABLE, report));

        report.vectorScopeObserved = false;
        assertEquals(OperationStateStore.State.FAILED,
                OperationStateStore.reconcileState(
                        OperationStateStore.OP_ONE_BACK_DISABLE, report));
    }
}
