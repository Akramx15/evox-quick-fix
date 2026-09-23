package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LeAudioStatusTest {
    @Test public void activeNeedsCurrentBootSuccessAllPoliciesAndNoPendingUpdate() {
        assertEquals(LeAudioStatus.State.ACTIVE, status(1, 0, 0, 1, 3, 1).state());
        assertEquals(LeAudioStatus.State.PENDING, status(1, 0, 0, 1, 3, 0).state());
        assertEquals(LeAudioStatus.State.PENDING, status(1, 0, 0, 1, 2, 1).state());
        assertEquals(LeAudioStatus.State.PENDING, status(1, 1, 0, 1, 3, 1).state());
    }

    @Test public void disableRetainsRecoveryAfterRomChanges() {
        LeAudioStatus changed = status(1, 0, 0, 0, 0, 0);
        assertFalse(changed.canApply());
        assertTrue(changed.canDisable());
        assertEquals(LeAudioStatus.State.DISABLE_PENDING, status(1, 0, 1, 1, 3, 1).state());
        assertEquals(LeAudioStatus.State.DISABLED, status(1, 0, 1, 1, 0, 0).state());
        assertFalse(status(1, 0, 1, 1, 0, 0).canDisable());
        assertEquals(LeAudioStatus.State.DISABLED, status(1, 0, 1, 0, 0, 0).state());
        assertFalse(status(1, 0, 1, 0, 0, 0).canApply());
    }

    @Test public void failedAndIncompleteInspectionNeverPermitsMutation() {
        for (CommandResult result : new CommandResult[]{
                new CommandResult(41, "LE_AUDIO_BLOCKED:unknown_module", false),
                new CommandResult(0, "present=0\nrom_ok=1", false),
                new CommandResult(0, "", true)}) {
            assertFalse(LeAudioStatus.parse(result).canApply());
            assertFalse(LeAudioStatus.parse(result).canDisable());
        }
    }

    @Test public void persistedOperationsReconcileAfterReboot() {
        DiagnosticReport report = new DiagnosticReport();
        report.leAudio = status(1, 0, 0, 1, 3, 1);
        assertEquals(OperationStateStore.State.APPLIED, OperationStateStore.reconcileState(
                OperationStateStore.OP_LE_AUDIO, report));
        report.leAudio = status(1, 0, 1, 1, 3, 1);
        assertEquals(OperationStateStore.State.REBOOT_REQUIRED, OperationStateStore.reconcileState(
                OperationStateStore.OP_LE_AUDIO_DISABLE, report));
        report.leAudio = status(1, 0, 1, 1, 0, 0);
        assertEquals(OperationStateStore.State.APPLIED, OperationStateStore.reconcileState(
                OperationStateStore.OP_LE_AUDIO_DISABLE, report));
    }

    private LeAudioStatus status(int present, int staged, int disabled, int rom,
                                 int patched, int ready) {
        return LeAudioStatus.parse(new CommandResult(0, "present=" + present + "\nstaged=" + staged
                + "\ndisabled=" + disabled + "\nremoved=0\nrom_ok=" + rom + "\npatched=" + patched
                + "\nready=" + ready + "\nboot_failed=0", false));
    }
}
